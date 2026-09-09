#!/usr/bin/env python3
"""Build a deployable archive exclusively from a Git commit, never local ignored files."""
from __future__ import annotations
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[1]
IMAGES = ("mysql:8.0.46", "redis:7.2-alpine", "eclipse-temurin:17-jre-jammy",
          "nginx:stable-alpine", "python:3.11-slim")


def run(args, cwd=None, env=None, capture=False):
    result = subprocess.run(args, cwd=cwd, env=env, check=True,
                            stdout=subprocess.PIPE if capture else None, text=True)
    return result.stdout.strip() if capture else None


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def archive_tree(source, output, epoch=0):
    with output.open("wb") as stream, gzip.GzipFile(filename="", fileobj=stream, mode="wb", mtime=epoch) as zipped:
        with tarfile.open(fileobj=zipped, mode="w", format=tarfile.PAX_FORMAT) as archive:
            for path in sorted(source.rglob("*")):
                if not path.is_file() or path.is_symlink():
                    continue
                info = archive.gettarinfo(str(path), path.relative_to(source).as_posix())
                info.mtime, info.uid, info.gid, info.uname, info.gname = epoch, 0, 0, "", ""
                info.mode = 0o755 if path.suffix == ".sh" else 0o644
                with path.open("rb") as body:
                    archive.addfile(info, body)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--ref", default="HEAD")
    parser.add_argument("--output", type=Path, default=ROOT / "dist-release")
    args = parser.parse_args()
    if not re.fullmatch(r"v\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", args.version):
        parser.error("version must be vMAJOR.MINOR.PATCH[-suffix]")
    commit = run(["git", "rev-parse", "--verify", args.ref + "^{commit}"], ROOT, capture=True)
    epoch = int(run(["git", "show", "-s", "--format=%ct", commit], ROOT, capture=True))
    args.output.mkdir(parents=True, exist_ok=True)
    args.output = args.output.resolve()
    with tempfile.TemporaryDirectory(prefix="rent-release-") as temporary:
        work = Path(temporary)
        source = work / "source"
        source.mkdir()
        export = work / "source.tar"
        run(["git", "archive", "--format=tar", "--output=" + str(export), commit], ROOT)
        with tarfile.open(export) as archive:
            for member in archive.getmembers():
                if member.issym() or member.islnk() or member.name.startswith("/") or ".." in Path(member.name).parts:
                    raise ValueError("Source export contains an unsafe path")
            archive.extractall(source)
        # .gitignore is not a release boundary: git archive above is the allowlist.
        maven = shutil.which("mvn.cmd") or shutil.which("mvn")
        npm = shutil.which("npm.cmd") or shutil.which("npm")
        if not maven or not npm:
            raise ValueError("Release builds require Maven, JDK 17, Node 22 and npm on PATH")
        run([maven, "-B", "-ntp", "-pl", "equipment-eureka,equipment-platform,equipment-alipay,equipment-gateway",
             "-am", "-DskipTests", "package"], source)
        frontend = source / "equipment_management_system_fornt"
        run([npm, "ci", "--no-audit", "--no-fund"], frontend)
        for app in ("platform", "rent"):
            env = os.environ.copy()
            env.update(VITE_BASE_URL=f"/{app}/", VITE_API_URL_PREFIX="/api", VITE_GATEWAY_ORIGIN="/gateway",
                       VITE_PLATFORM_ORIGIN="", VITE_PLATFORM_PUBLIC_PATH="/platform/")
            run([npm, "--workspace", f"@ems/{app}-tdesign", "run", "build"], frontend, env)
        payload = work / "payload"
        payload.mkdir()
        deploy = source / "customer-deploy"
        for name in ("docker-compose.yml", "Dockerfile.tools", ".env.example", "README.md", "install.sh", "upgrade.sh", "rollback.sh"):
            shutil.copy2(deploy / name, payload / name)
        for name in ("scripts", "sql", "nginx"):
            shutil.copytree(deploy / name, payload / name, ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
        for name in ("LICENSE", "NOTICE", "SECURITY.md"):
            if (source / name).is_file(): shutil.copy2(source / name, payload / name)
        if (source / "LICENSES").exists(): shutil.copytree(source / "LICENSES", payload / "LICENSES")
        (payload / "jars").mkdir()
        for module in ("equipment-eureka", "equipment-platform", "equipment-alipay", "equipment-gateway"):
            shutil.copy2(source / module / "target" / f"{module}-0.0.1-SNAPSHOT.jar", payload / "jars" / f"{module}.jar")
        for app in ("platform", "rent"):
            shutil.copytree(frontend / "apps" / f"{app}-tdesign" / "dist", payload / "www" / app)
        (payload / "health").mkdir()
        run(["javac", "--release", "17", "-d", str(payload / "health"), str(deploy / "scripts/HttpHealth.java")])
        images = {}
        for image in IMAGES:
            run(["docker", "pull", image])
            refs = json.loads(run(["docker", "image", "inspect", "--format", "{{json .RepoDigests}}", image], capture=True))
            if not refs or not re.fullmatch(r"[a-zA-Z0-9./_-]+@sha256:[a-f0-9]{64}", refs[0]):
                raise ValueError("Missing registry digest for base image")
            images[image] = refs[0]
        for name in ("docker-compose.yml", "Dockerfile.tools"):
            content = (payload / name).read_text(encoding="utf-8")
            for original, pinned in images.items(): content = content.replace(original, pinned)
            (payload / name).write_text(content, encoding="utf-8", newline="\n")
        (payload / "images.lock.json").write_text(json.dumps(images, indent=2) + "\n", encoding="utf-8")
        (payload / "SOURCE_COMMIT").write_text(commit + "\n", encoding="ascii")
        # Normalize scripts even when the source checkout uses Windows line endings.
        for path in payload.rglob("*"):
            if path.is_file() and path.suffix in {".py", ".sh", ".yml", ".sql"}:
                path.write_bytes(path.read_bytes().replace(b"\r\n", b"\n"))
        initializer = (payload / "scripts/initialize.py").read_text(encoding="utf-8")
        schema_version = re.search(r'^VERSION = "([^"]+)"', initializer, re.M).group(1)
        manifest = {"format": 1, "version": args.version, "schema_version": schema_version,
                    "source_commit": commit, "files": {p.relative_to(payload).as_posix(): digest(p)
                    for p in sorted(payload.rglob("*")) if p.is_file()}}
        (payload / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        filename = f"rent-project-{args.version}-docker.tar.gz"
        archive_tree(payload, args.output / filename, epoch)
        (args.output / "SHA256SUMS").write_text(f"{digest(args.output / filename)}  {filename}\n", encoding="ascii")
        print(f"Release package: {filename}; source: {commit}; SHA256: {digest(args.output / filename)}")


if __name__ == "__main__":
    main()
