#!/usr/bin/env python3
"""Integration test against disposable local Docker services, never a remote database."""
from __future__ import annotations
import argparse
import importlib.util
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request

from build_release import archive_tree, digest


def request(base, path, data=None):
    headers = {"Content-Type": "application/json", "X-Login-System": "platform"}
    body = json.dumps(data).encode() if data is not None else None
    with urllib.request.urlopen(urllib.request.Request(base + path, data=body, headers=headers), timeout=20) as response:
        return response.read()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("archive", type=Path)
    args = p.parse_args()
    script = Path(__file__).resolve().parents[1] / "customer-deploy/scripts/deploy.py"
    spec = importlib.util.spec_from_file_location("deploy", script)
    deploy = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(deploy)
    password = "CI-only!" + secrets.token_hex(16)
    environment = dict(os.environ, EMS_INIT_ADMIN_PASSWORD=password)
    with tempfile.TemporaryDirectory(prefix="rent-docker-smoke-") as temp:
        root = Path(temp) / "installation"
        archive = args.archive.resolve()
        base = "http://127.0.0.1:18080"

        def engine(command, *options, success=True):
            result = subprocess.run([sys.executable, str(script), command, "--root", str(root), *map(str, options)], env=environment)
            if (result.returncode == 0) != success:
                raise AssertionError(f"Unexpected result from {command}")

        def compose():
            return deploy.Compose(root, deploy.selected_release(root))

        def sql(statement):
            c = compose()
            return c.run("exec", "-T", "-e", "MYSQL_PWD", "mysql", "mysql", "-u", "root", "-Nse", statement,
                         extra={"MYSQL_PWD": c.values["MYSQL_ROOT_PASSWORD"]}).stdout.decode().strip()

        def login():
            result = json.loads(request(base, "/gateway/api/login/login", {"userName": "admin", "userPwd": password}))
            assert result.get("code") == 0 and result.get("data", {}).get("token"), "Administrator login failed"

        def repack(version, broken=False, incompatible=False):
            # Build a synthetic application release for the same installed database.
            import shutil
            path = Path(temp) / version
            shutil.copytree(deploy.selected_release(root), path)
            (path / ".release.json").unlink()
            manifest = json.loads((path / "manifest.json").read_text())
            manifest["version"] = version
            if incompatible:
                manifest["schema_version"] = "incompatible-test-schema"
            if broken:
                (path / "jars/equipment-gateway.jar").write_bytes(b"intentionally invalid CI jar")
            manifest["files"] = {f.relative_to(path).as_posix(): digest(f) for f in path.rglob("*")
                                 if f.is_file() and f.name != "manifest.json"}
            (path / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            result = Path(temp) / (version + ".tar.gz")
            archive_tree(path, result)
            return result

        try:
            print("SMOKE: fresh managed installation and administrator login", flush=True)
            engine("install", archive, "--sha256", digest(archive), "--http-port", "18080", "--demo")
            engine("check")
            engine("status")
            assert b"<html" in request(base, "/platform/").lower()
            assert b"<html" in request(base, "/rent/").lower()
            login()
            try:
                request(base, "/gateway/actuator/health")
                raise AssertionError("Management API was exposed")
            except urllib.error.HTTPError as error:
                assert error.code == 404
            original = deploy.selected_release(root).name
            configuration = (root / ".env").read_bytes()
            # Validate the external profile without connecting to any external database.
            external = deploy.Compose(root, deploy.selected_release(root))
            external.prefix = external.prefix[:-2]  # remove the managed profile selection
            external.environment.update(DB_MODE="external", RDS_HOST="mysql.example.com", DB_SSL_MODE="VERIFY_IDENTITY")
            external.run("config", "--quiet")
            uploads = root / "shared/uploads/smoke-preserved.txt"
            uploads.write_text("preserve uploads across releases", encoding="utf-8")
            os.chmod(root / "shared/uploads", 0o755)
            os.chmod(uploads, 0o644)
            sql("CREATE TABLE equipment_platform.release_smoke (id INT PRIMARY KEY, value VARCHAR(50)); INSERT INTO equipment_platform.release_smoke VALUES(1,'preserved across upgrade')")
            data_containers = compose().run("ps", "-q", "mysql", "redis").stdout

            print("SMOKE: reject a schema-incompatible package without touching services", flush=True)
            incompatible = repack("v0.0.0-smoke-schema", incompatible=True)
            engine("upgrade", incompatible, "--sha256", digest(incompatible), success=False)
            assert deploy.selected_release(root).name == original
            login()

            print("SMOKE: upgrade preserves configuration, database rows, uploads and data containers", flush=True)
            good = repack("v0.0.0-smoke-upgrade")
            engine("upgrade", good, "--sha256", digest(good))
            assert (root / ".env").read_bytes() == configuration
            assert sql("SELECT value FROM equipment_platform.release_smoke WHERE id=1") == "preserved across upgrade"
            assert request(base, "/uploads/smoke-preserved.txt") == uploads.read_bytes()
            assert compose().run("ps", "-q", "mysql", "redis").stdout == data_containers
            backups = list((root / "backups").glob("*/backup.json"))
            assert backups and all(json.loads(path.read_text())["complete"] for path in backups)
            login()

            print("SMOKE: explicit program rollback", flush=True)
            engine("rollback", "--version", original)
            assert deploy.selected_release(root).name == original
            assert (root / ".env").read_bytes() == configuration
            login()

            print("SMOKE: unhealthy application release automatically restores the previous version", flush=True)
            broken = repack("v0.0.0-smoke-broken", broken=True)
            engine("upgrade", broken, "--sha256", digest(broken), "--timeout", "120", success=False)
            assert deploy.selected_release(root).name == original
            assert json.loads((root / "deploy-state.json").read_text())["status"] == "rolled_back_after_failure"
            assert sql("SELECT COUNT(*) FROM equipment_platform.release_smoke") == "1"
            assert (root / ".env").read_bytes() == configuration
            login()
            print("Docker release smoke tests passed.", flush=True)
        except Exception:
            # Retain no deployment artifacts on public Actions. Print only redacted Compose errors/state.
            if (root / ".env").exists():
                values = deploy.read_env(root / ".env")
                release = next((root / "releases").glob("v*"))
                c = deploy.Compose(root, release)
                private_log = root / "deploy-private.log"
                if private_log.exists():
                    output = private_log.read_text(encoding="utf-8", errors="replace")[-10000:]
                    for key, value in values.items():
                        if value and any(part in key for part in ("PASSWORD", "SECRET")):
                            output = output.replace(value, "<redacted>")
                    print(output.replace(password, "<redacted>"))
                for options in [("ps", "-a"), ("logs", "--tail", "15", "equipment-eureka", "equipment-platform", "equipment-alipay", "equipment-gateway")]:
                    result = subprocess.run(c.prefix + list(options), env=c.environment, capture_output=True, text=True)
                    output = result.stdout + result.stderr
                    for key, value in values.items():
                        if value and any(part in key for part in ("PASSWORD", "SECRET")):
                            output = output.replace(value, "<redacted>")
                    print(output.replace(password, "<redacted>"))
            raise
        finally:
            if (root / ".env").exists():
                releases = list((root / "releases").glob("v*"))
                if releases:
                    c = deploy.Compose(root, releases[0])
                    # Removes only containers/networks scoped to this newly generated CI project.
                    subprocess.run(c.prefix + ["down", "--remove-orphans"], env=c.environment, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            # Containers can create root-owned data; cleanup only the exact disposable test directory.
            if os.name == "posix" and root.exists():
                subprocess.run(["sudo", "chmod", "-R", "u+rwX", str(root)], check=False)
                subprocess.run(["sudo", "chown", "-R", f"{os.getuid()}:{os.getgid()}", str(root)], check=False)


if __name__ == "__main__":
    main()
