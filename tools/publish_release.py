#!/usr/bin/env python3
"""Publish an explicitly selected successful package run from this repository."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

from check_release import check_archive


def gh(*arguments):
    return subprocess.check_output(["gh", *arguments], text=True).strip()


def sha256(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def reserve_tag(repository, version, commit):
    # Create the exact reference atomically. An existing tag (with or without a
    # Release) causes GitHub to reject this POST; never reuse or move that tag.
    result = json.loads(gh("api", "--method", "POST", f"repos/{repository}/git/refs",
                           "-f", "ref=refs/tags/" + version, "-f", "sha=" + commit))
    if result.get("ref") != "refs/tags/" + version or result.get("object", {}).get("sha") != commit:
        raise ValueError("New tag does not select the verified source commit")


def require_new_version(repository, version):
    pages = json.loads(gh("api", "--paginate", "--slurp", f"repos/{repository}/releases?per_page=100"))
    if any(release["tag_name"] == version for page in pages for release in page):
        raise ValueError("A draft or published Release already uses this version")


def verify_existing_tag(repository, version, commit):
    result = json.loads(gh("api", f"repos/{repository}/git/ref/tags/{version}"))
    if (result.get("ref") != "refs/tags/" + version
            or result.get("object", {}).get("type") != "commit"
            or result.get("object", {}).get("sha") != commit):
        raise ValueError("The explicitly selected existing lightweight tag must equal SOURCE_COMMIT")


def main():
    repository = os.environ["GITHUB_REPOSITORY"]
    run_id, version = os.environ["SOURCE_RUN"], os.environ["RELEASE_VERSION"]
    if os.environ.get("GITHUB_REF") != "refs/heads/main":
        raise ValueError("Publication must run from main")
    if not re.fullmatch(r"\d+", run_id) or not re.fullmatch(r"v\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", version):
        raise ValueError("Invalid source run or release version")
    source = json.loads(gh("api", f"repos/{repository}/actions/runs/{run_id}"))
    if (source["conclusion"] != "success" or source["status"] != "completed"
            or source["path"] != ".github/workflows/docker-release.yml"
            or source["head_branch"] != "main" or source["head_repository"]["full_name"] != repository):
        raise ValueError("Source must be a successful Docker package run on this repository's main branch")
    with tempfile.TemporaryDirectory(prefix="rent-publish-") as temporary:
        directory = Path(temporary)
        gh("run", "download", run_id, "--repo", repository, "--name", "docker-install-package", "--dir", str(directory))
        filename = f"rent-project-{version}-docker.tar.gz"
        archive, checksums = directory / filename, directory / "SHA256SUMS"
        if {path.name for path in directory.iterdir()} != {filename, "SHA256SUMS"}:
            raise ValueError("Unexpected publication artifact contents")
        digest = sha256(archive)
        if checksums.read_text(encoding="ascii").split() != [digest, filename]:
            raise ValueError("Package checksum mismatch")
        if check_archive(archive):
            raise ValueError("Package publication scan failed")
        import tarfile
        with tarfile.open(archive) as packed:
            manifest = json.load(packed.extractfile("manifest.json"))
        if manifest["version"] != version or manifest["source_commit"] != source["head_sha"]:
            raise ValueError("Package version or source commit does not match the selected run")
        from audit_release import audit_package
        report = Path(os.getenv("PUBLICATION_REPORT", "dist-audit/publication-audit.json"))
        audit_package(archive, Path(os.environ["GITLEAKS_BINARY"]), report)
        if os.getenv("RELEASE_PUBLISH", "false") != "true":
            print("Package audit completed. No Git tag or Release was created.")
            return
        notes = directory / "release-notes.md"
        notes.write_text(f"""Docker 安装包，包含四个后端服务、两个管理前端和初始化工具。

- `install.sh`：默认内置 MySQL / Redis，也支持外部 MySQL / RDS；可继续未完成的首次安装。
- `upgrade.sh`：校验发行包，停写备份两个数据库、配置与上传文件，保留数据和密钥。
- `rollback.sh`：回滚应用版本；新应用健康检查失败时自动恢复旧版本。
- 支持可选虚构演示数据、状态检查、独立备份与启停。

安装机需要 Linux、Python 3.10+、Docker Engine 和 Compose 2.20+。首次联网拉取镜像，不需要安装 Java、Maven 或 Node.js。默认入口只绑定 `127.0.0.1:8080`。

下载 `.tar.gz` 和 `SHA256SUMS` 后先执行 `sha256sum -c SHA256SUMS`，再按[安装、升级与回滚手册](https://honesttai.github.io/rent-project/DEPLOYMENT/)操作。

隔离 Linux / amd64 Docker 实测已通过安装登录、升级数据保留、主动回滚及损坏新版本后的自动恢复。外部 RDS 完成配置预检；真实支付、代扣和电子签需自行配置授权。

源码：`{source['head_sha']}`。SHA256：`{digest}`。

[构建与集成验证](https://github.com/{repository}/actions/runs/{run_id}) · [公开内容复查](https://honesttai.github.io/rent-project/SECURITY_REVIEW/) · [操作手册](https://honesttai.github.io/rent-project/USER_GUIDE/)

honestTai · honest.tai@outlook.com。感谢 [HRouter](https://hrouter.net) 赞助，欢迎关注同作者的 [geo-console](https://github.com/honestTai/geo-console)。
""", encoding="utf-8")
        # A new draft is required. Existing versions are never overwritten by a retry.
        require_new_version(repository, version)
        if os.getenv("USE_EXISTING_VERIFIED_TAG", "false") == "true":
            verify_existing_tag(repository, version, source["head_sha"])
        else:
            reserve_tag(repository, version, source["head_sha"])
        gh("release", "create", version, "--repo", repository, "--draft",
           "--verify-tag", "--title", version + " · Docker 安装、升级与回滚", "--notes-file", str(notes))
        gh("release", "upload", version, str(archive), str(checksums), "--repo", repository)
        releases = json.loads(gh("api", f"repos/{repository}/releases?per_page=100"))
        drafts = [release for release in releases if release["tag_name"] == version and release["draft"]]
        if len(drafts) != 1:
            raise ValueError("New draft could not be identified")
        assets = drafts[0]["assets"]
        expected = {filename: digest, "SHA256SUMS": sha256(checksums)}
        if {asset["name"] for asset in assets} != set(expected):
            raise ValueError("Draft asset inventory mismatch; draft retained for inspection")
        for asset in assets:
            if asset.get("digest") != "sha256:" + expected[asset["name"]]:
                raise ValueError("Uploaded asset digest mismatch; draft retained for inspection")
        gh("release", "edit", version, "--repo", repository, "--draft=false", "--latest")
        print(f"Published https://github.com/{repository}/releases/tag/{version}")


if __name__ == "__main__":
    main()
