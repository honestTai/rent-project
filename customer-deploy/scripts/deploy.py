#!/usr/bin/env python3
"""Verified Linux Compose releases with persistent configuration and program rollback."""
from __future__ import annotations

import argparse
import contextlib
import datetime as dt
import getpass
import gzip
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import secrets
import shutil
import signal
import subprocess
import sys
import tarfile
import tempfile
import uuid

APPLICATIONS = ["equipment-eureka", "equipment-platform", "equipment-alipay", "equipment-gateway", "nginx"]
VERSION_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}\Z")
MAX_BYTES = 4 * 1024 ** 3
MAX_FILES = 20000


class DeployError(Exception):
    pass


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_name(name):
    if not isinstance(name, str) or "\\" in name or ":" in name or "\x00" in name:
        raise DeployError("Unsafe archive path")
    while name.startswith("./"):
        name = name[2:]
    path = PurePosixPath(name)
    if not name or path.is_absolute() or any(part in {"..", "."} for part in name.split("/")):
        raise DeployError("Unsafe archive path")
    if any(part in {".env", ".git", ".artifacts", "shared", "deploy-state.json", ".release.json"} for part in path.parts):
        raise DeployError("Private or runtime files must not appear in a release")
    return path.as_posix().rstrip("/")


def manifest_valid(manifest):
    if not isinstance(manifest, dict) or manifest.get("format") != 1:
        raise DeployError("Unsupported release manifest format")
    for key in ("version", "schema_version"):
        if not isinstance(manifest.get(key), str) or not VERSION_RE.fullmatch(manifest[key]):
            raise DeployError("Invalid release version or schema version")
    files = manifest.get("files")
    if not isinstance(files, dict) or not files or len(files) > MAX_FILES:
        raise DeployError("Invalid release file inventory")
    for name, digest in files.items():
        if safe_name(name) != name or name == "manifest.json" or not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest):
            raise DeployError("Invalid release file digest or path")
    required = {"docker-compose.yml", "scripts/initialize.py", "sql/init.sql", "sql/seed-platform.sql", "sql/seed-alipay.sql", "sql/demo.sql", "www/platform/index.html", "www/rent/index.html"}
    required.update(f"jars/{name}.jar" for name in APPLICATIONS if name != "nginx")
    if not required.issubset(files):
        raise DeployError("Release is missing required application or initialization files")
    return manifest


def inspect_archive(archive, expected):
    if not re.fullmatch(r"[0-9a-fA-F]{64}", expected or "") or sha256(archive) != expected.lower():
        raise DeployError("Archive SHA256 does not match the expected release checksum")
    members, total = {}, 0
    with tarfile.open(archive, "r:gz") as source:
        for member in source:
            if member.isdir() and member.name in {".", "./"}:
                continue
            name = safe_name(member.name.rstrip("/"))
            if name in members or not (member.isfile() or member.isdir()):
                raise DeployError("Duplicate paths, links and special archive entries are forbidden")
            total += member.size
            if total > MAX_BYTES or len(members) >= MAX_FILES or member.size < 0:
                raise DeployError("Release exceeds extraction limits")
            members[name] = member
        entry = members.get("manifest.json")
        if not entry or not entry.isfile() or entry.size > 2 * 1024 ** 2:
            raise DeployError("Release manifest is missing or too large")
        manifest = manifest_valid(json.load(source.extractfile(entry)))
        inventory = {name for name, member in members.items() if member.isfile() and name != "manifest.json"}
        if inventory != set(manifest["files"]):
            raise DeployError("Archive and manifest file inventories differ")
        for name in members:
            parent = PurePosixPath(name).parent
            while parent != PurePosixPath("."):
                if parent.as_posix() in members and not members[parent.as_posix()].isdir():
                    raise DeployError("An archive file cannot be the parent of another entry")
                parent = parent.parent
    return manifest


def verify_release(path):
    path = Path(path)
    if path.is_symlink():
        raise DeployError("Release directory must not be a symbolic link")
    manifest = manifest_valid(json.loads((path / "manifest.json").read_text(encoding="utf-8")))
    actual = set()
    for item in path.rglob("*"):
        if item.is_symlink():
            raise DeployError("Release directory contains a symbolic link")
        if item.is_file():
            name = item.relative_to(path).as_posix()
            if name not in {"manifest.json", ".release.json"}:
                actual.add(name)
    if actual != set(manifest["files"]):
        raise DeployError("Installed release file inventory has changed")
    for name, digest in manifest["files"].items():
        if sha256(path / name) != digest:
            raise DeployError("Installed release failed file integrity verification")
    return manifest


def atomic_json(path, value):
    temporary = path.with_name(path.name + ".tmp-" + uuid.uuid4().hex)
    try:
        with temporary.open("x", encoding="utf-8", newline="\n") as stream:
            os.chmod(temporary, 0o600)
            json.dump(value, stream, indent=2, ensure_ascii=False)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def stage_release(root, archive, expected):
    manifest = inspect_archive(archive, expected)
    releases = root / "releases"
    releases.mkdir(exist_ok=True)
    if releases.is_symlink():
        raise DeployError("Release storage must not be a symbolic link")
    destination = releases / manifest["version"]
    if destination.exists():
        if destination.is_symlink():
            raise DeployError("Release directory must not be a symbolic link")
        metadata = json.loads((destination / ".release.json").read_text(encoding="utf-8"))
        if metadata.get("archive_sha256") != expected.lower():
            raise DeployError("This release version already exists with different contents")
        verify_release(destination)
        return destination, manifest
    staging = releases / (".staging-" + uuid.uuid4().hex)
    staging.mkdir(mode=0o700)
    try:
        with tarfile.open(archive, "r:gz") as source:
            for member in source:
                if not member.isfile():
                    continue
                name = safe_name(member.name)
                target = staging / name
                target.parent.mkdir(parents=True, exist_ok=True)
                with source.extractfile(member) as src, target.open("xb") as dst:
                    shutil.copyfileobj(src, dst, 1024 * 1024)
                os.chmod(target, 0o755 if name.endswith((".sh", ".py")) else 0o644)
        if verify_release(staging) != manifest or sha256(archive) != expected.lower():
            raise DeployError("Archive changed during extraction")
        for directory in staging.rglob("*"):
            if directory.is_dir():
                os.chmod(directory, 0o755)
        os.chmod(staging, 0o755)
        atomic_json(staging / ".release.json", {"archive_sha256": expected.lower()})
        os.replace(staging, destination)
        return destination, manifest
    finally:
        if staging.exists():
            if staging.parent.resolve() != releases.resolve() or not staging.name.startswith(".staging-"):
                raise DeployError("Refusing unsafe staging cleanup")
            shutil.rmtree(staging)


@contextlib.contextmanager
def deployment_lock(root):
    import fcntl
    root.mkdir(parents=True, exist_ok=True)
    lock = root / ".deploy.lock"
    if lock.is_symlink():
        raise DeployError("Deployment lock must not be a symbolic link")
    with lock.open("a+") as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise DeployError("Another deployment operation holds the installation lock") from None
        stream.seek(0)
        stream.truncate()
        stream.write(str(os.getpid()))
        stream.flush()
        yield


def atomic_link(root, name, release):
    if release.parent.resolve() != (root / "releases").resolve():
        raise DeployError("Release is outside the installation")
    current = root / name
    if current.exists() and not current.is_symlink():
        raise DeployError("Release pointer must be a symbolic link")
    temporary = root / ("." + name + "-" + uuid.uuid4().hex)
    try:
        temporary.symlink_to(Path("releases") / release.name, target_is_directory=True)
        os.replace(temporary, current)
    finally:
        temporary.unlink(missing_ok=True)


def selected_release(root, name="current"):
    pointer = root / name
    if not pointer.is_symlink():
        raise DeployError("No installed release is selected")
    path = pointer.resolve()
    if path.parent != (root / "releases").resolve() or not path.is_dir():
        raise DeployError("Invalid installed release pointer")
    return path


def read_env(path):
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or not re.fullmatch(r"[A-Z][A-Z0-9_]*", key):
            raise DeployError("Invalid deployment environment file")
        if value.startswith('"'):
            value = json.loads(value).replace("$$", "$")
        elif value.startswith("'") and value.endswith("'"):
            value = value[1:-1]
        if "\n" in value or "\r" in value or "\x00" in value:
            raise DeployError("Multiline environment values are unsupported")
        result[key] = value
    return result


def write_env(path, values):
    if path.exists() or path.is_symlink():
        raise DeployError("An existing environment file will never be overwritten")
    lines = []
    for key, value in sorted(values.items()):
        value = str(value)
        if not re.fullmatch(r"[A-Z][A-Z0-9_]*", key) or any(c in value for c in "\r\n\x00"):
            raise DeployError("Invalid environment key or multiline value")
        lines.append(key + "=" + json.dumps(value.replace("$", "$$"), ensure_ascii=False) + "\n")
    temporary = path.with_name(path.name + ".tmp-" + uuid.uuid4().hex)
    try:
        with temporary.open("x", encoding="utf-8", newline="\n") as stream:
            os.chmod(temporary, 0o600)
            stream.writelines(lines)
            stream.flush()
            os.fsync(stream.fileno())
        # link() publishes the complete file and atomically refuses an existing destination.
        os.link(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


class Compose:
    def __init__(self, root, release):
        self.root, self.release = root, release
        self.values = read_env(root / ".env")
        self.environment = dict(os.environ, **self.values)
        self.environment.update(RELEASE_DIR=str(release), SHARED_DIR=str(root / "shared"), RELEASE_VERSION=release.name)
        self.prefix = ["docker", "compose", "--project-name", self.values["PROJECT_NAME"], "--env-file", str(root / ".env"), "-f", str(release / "docker-compose.yml")]
        if self.values["DB_MODE"] == "managed":
            self.prefix += ["--profile", "managed"]

    def run(self, *arguments, extra=None, stdout=None):
        environment = dict(self.environment, **(extra or {}))
        result = subprocess.run(self.prefix + list(arguments), env=environment, stdout=stdout or subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode:
            log = self.root / "deploy-private.log"
            if log.is_symlink():
                raise DeployError("Docker operation failed; refusing to write diagnostics through a symbolic link")
            with log.open("ab") as stream:
                os.chmod(log, 0o600)
                stream.write(("\n" + dt.datetime.now(dt.timezone.utc).isoformat() + " " + self.release.name + " " + " ".join(arguments[:2]) + "\n").encode())
                if stdout is None and isinstance(result.stdout, bytes):
                    stream.write(result.stdout)
                stream.write(result.stderr or b"")
            raise DeployError("Docker Compose operation failed: " + " ".join(arguments[:2]) + ". Private diagnostic log (may contain secrets): " + str(log))
        return result

    def prepare(self, bootstrap=False):
        self.run("config", "--quiet")
        self.run("pull", *self.services())
        if bootstrap:
            self.run("build", "bootstrap-" + self.values["DB_MODE"])

    def services(self):
        return (["mysql"] if self.values["DB_MODE"] == "managed" else []) + ["redis"] + APPLICATIONS

    def start(self, timeout, recreate=False):
        self.run("up", "-d", "--wait", "--wait-timeout", str(timeout), *(["--force-recreate", "--no-deps"] if recreate else []), *(APPLICATIONS if recreate else self.services()))


def backup(root, compose):
    backups = root / "backups"
    backups.mkdir(mode=0o700, exist_ok=True)
    if backups.is_symlink():
        raise DeployError("Backup directory must not be a symbolic link")
    target = backups / (dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8])
    target.mkdir(mode=0o700)
    try:
        with tempfile.TemporaryFile() as output:
            compose.run("run", "-T", "--rm", "--no-deps", "db-backup", stdout=output)
            if output.tell() == 0:
                raise DeployError("Database backup was empty")
            output.seek(0)
            with gzip.open(target / "databases.sql.gz", "wb") as compressed:
                shutil.copyfileobj(output, compressed, 1024 * 1024)
        shutil.copy2(root / ".env", target / ".env")
        os.chmod(target / ".env", 0o600)
        with tarfile.open(target / "uploads.tar.gz", "w:gz", dereference=False) as archive:
            uploads = root / "shared" / "uploads"
            if uploads.exists():
                archive.add(uploads, arcname="uploads")
        atomic_json(target / "backup.json", {"complete": True, "version": compose.release.name, "database_sha256": sha256(target / "databases.sql.gz"), "uploads_sha256": sha256(target / "uploads.tar.gz"), "redis": "retained in shared/redis; no automatic restore"})
    except Exception:
        atomic_json(target / "backup.json", {"complete": False, "version": compose.release.name})
        raise
    return target


def apply_release(root, release, manifest, timeout, compose_factory=Compose, backup_function=backup):
    old = selected_release(root)
    old_manifest = verify_release(old)
    if old_manifest["schema_version"] != manifest["schema_version"]:
        raise DeployError("Database schema versions differ. Automatic database migration is not supported")
    if old == release:
        raise DeployError("This release is already active")
    newer, previous = compose_factory(root, release), compose_factory(root, old)
    newer.prepare()
    stopped = False
    try:
        stopped = True
        previous.run("stop", *APPLICATIONS)
        snapshot = backup_function(root, previous)
        atomic_link(root, "current", release)
        newer.start(timeout, recreate=True)
    except (Exception, KeyboardInterrupt) as original:
        if stopped:
            atomic_link(root, "current", old)
            try:
                previous.start(timeout, recreate=True)
            except Exception:
                atomic_json(root / "deploy-state.json", {"status": "recovery_required", "current": old.name, "failed_release": release.name})
                raise DeployError("Deployment failed and the previous services did not recover. The current pointer selects the previous release; data and backups are retained") from None
            atomic_json(root / "deploy-state.json", {"status": "rolled_back_after_failure", "current": old.name, "failed_release": release.name})
        raise DeployError("Deployment failed; the previous release has been restored. " + str(original)) from None
    atomic_link(root, "previous", old)
    atomic_json(root / "deploy-state.json", {"status": "healthy", "current": release.name, "previous": old.name, "schema_version": manifest["schema_version"], "backup": str(snapshot.relative_to(root))})


def ask_secret(environment, prompt, confirm=False):
    value = os.environ.get(environment)
    if value is None:
        if not sys.stdin.isatty():
            raise DeployError("A terminal is required for hidden password input; unattended runs may provide the documented password environment variable")
        value = getpass.getpass(prompt)
        if confirm and value != getpass.getpass("Confirm password: "):
            raise DeployError("Passwords do not match")
    if not value or any(c in value for c in "\r\n\x00"):
        raise DeployError("A nonempty, single-line password is required")
    return value


def install(root, release, manifest, args):
    if args is not None and args.resume:
        return resume_install(root, release, manifest, args)
    if (root / ".env").exists() or (root / "current").is_symlink():
        raise DeployError("This installation already has configuration. Use upgrade; existing databases are never reinitialized")
    admin = ask_secret("EMS_INIT_ADMIN_PASSWORD", "New administrator password (12-128 characters, with punctuation): ", True)
    if not 12 <= len(admin) <= 128 or admin != admin.strip() or re.fullmatch(r"[A-Za-z0-9+/=]+", admin):
        raise DeployError("Administrator password needs 12-128 characters and punctuation other than + / =")
    if args.demo and args.http_bind not in {"127.0.0.1", "::1"}:
        raise DeployError("Demo installation must bind HTTP to loopback")
    if args.demo and args.db_mode != "managed":
        raise DeployError("Demo installation requires the managed, isolated MySQL service")
    template = release / ".env.example"
    values = read_env(template) if template.exists() else {}
    values.update(PROJECT_NAME="rent_" + hashlib.sha256(str(root).encode()).hexdigest()[:12], DB_MODE=args.db_mode, MYSQL_ROOT_PASSWORD=secrets.token_hex(32), RDS_PASSWORD=secrets.token_hex(32), REDIS_PASSWORD=secrets.token_hex(32), RENTAL_PRODUCT_AES_SECRET=secrets.token_hex(32), RDS_HOST="mysql", RDS_PORT="3306", RDS_USERNAME="rent_app", RDS_PLATFORM_DATABASE=args.platform_db, RDS_ALIPAY_DATABASE=args.alipay_db, HTTP_BIND=args.http_bind, HTTP_PORT=str(args.http_port), DB_SSL_MODE="DISABLED", DB_SSL_CA="", DB_JDBC_OPTIONS="", ADMIN_USER=args.admin_user, BOOTSTRAP_DEMO="1" if args.demo else "0")
    init_user, init_password = "root", values["MYSQL_ROOT_PASSWORD"]
    if args.db_mode == "external":
        if not args.db_host or not args.db_user:
            raise DeployError("External MySQL requires --db-host and --db-user")
        values.update(RDS_HOST=args.db_host, RDS_PORT=str(args.db_port), RDS_USERNAME=args.db_user, RDS_PASSWORD=ask_secret("EMS_DEPLOY_DB_PASSWORD", "Application MySQL password: "), DB_SSL_MODE="VERIFY_IDENTITY")
        init_user = args.init_user or args.db_user
        init_password = os.environ.get("EMS_INIT_DB_PASSWORD") or (values["RDS_PASSWORD"] if init_user == args.db_user else ask_secret("EMS_INIT_DB_PASSWORD", "Initialization MySQL password: "))
    shared = root / "shared"
    if shared.exists() and (shared.is_symlink() or any(shared.iterdir())):
        raise DeployError("Installation shared storage must be new and empty")
    for name in ["mysql", "redis", "uploads", "logs/nginx", "tls"] + ["logs/" + service for service in APPLICATIONS if service != "nginx"]:
        (shared / name).mkdir(parents=True, exist_ok=True)
    os.chmod(shared / "uploads", 0o755)
    if args.ssl_ca:
        shutil.copyfile(Path(args.ssl_ca).resolve(strict=True), shared / "tls" / "mysql-ca.pem")
        values["DB_SSL_CA"] = "/run/db-ca/mysql-ca.pem"
        values["DB_JDBC_OPTIONS"] = "&trustCertificateKeyStoreUrl=file:/run/db-ca/mysql-truststore.p12&trustCertificateKeyStorePassword=changeit&trustCertificateKeyStoreType=PKCS12"
    write_env(root / ".env", values)
    state = {"status": "installing", "current": None, "release": release.name, "bootstrap_complete": False, "init_user": init_user}
    atomic_json(root / "deploy-state.json", state)
    finish_install(root, release, manifest, args.timeout, state, admin, init_password)


def resume_install(root, release, manifest, args):
    state = json.loads((root / "deploy-state.json").read_text(encoding="utf-8"))
    if state.get("status") not in {"installing", "installation_failed"} or state.get("release") != release.name:
        raise DeployError("Resume is restricted to the same unfinished first installation; an existing healthy installation cannot be initialized again")
    values = read_env(root / ".env")
    if (root / "current").is_symlink() and selected_release(root) != release:
        raise DeployError("Resume cannot replace an existing active release")
    admin = init_password = None
    if not state.get("bootstrap_complete"):
        admin = ask_secret("EMS_INIT_ADMIN_PASSWORD", "Original administrator password (unchanged if initialization already completed): ", True)
        if not 12 <= len(admin) <= 128 or admin != admin.strip() or re.fullmatch(r"[A-Za-z0-9+/=]+", admin):
            raise DeployError("Administrator password needs 12-128 characters and punctuation other than + / =")
        if values["DB_MODE"] == "managed":
            init_password = values["MYSQL_ROOT_PASSWORD"]
        elif state["init_user"] == values["RDS_USERNAME"]:
            init_password = os.environ.get("EMS_INIT_DB_PASSWORD") or values["RDS_PASSWORD"]
        else:
            init_password = ask_secret("EMS_INIT_DB_PASSWORD", "Initialization MySQL password: ")
    print("Resuming the recorded first installation with its existing configuration and secrets.")
    finish_install(root, release, manifest, args.timeout, state, admin, init_password)


def finish_install(root, release, manifest, timeout, state, admin, init_password):
    compose = Compose(root, release)
    values = compose.values
    mode = values["DB_MODE"]
    state["status"] = "installing"
    atomic_json(root / "deploy-state.json", state)
    try:
        compose.prepare(bootstrap=not state.get("bootstrap_complete"))
        compose.run("up", "-d", "--wait", "--wait-timeout", str(timeout), *(["mysql"] if mode == "managed" else []), "redis")
        if not state.get("bootstrap_complete"):
            compose.run("run", "-T", "--rm", "--no-deps", "-e", "EMS_INIT_ADMIN_PASSWORD", "-e", "EMS_INIT_DB_PASSWORD", "-e", "EMS_INIT_DB_USERNAME", "-e", "BOOTSTRAP_DEMO", "-e", "EMS_INIT_ADMIN_USERNAME", "bootstrap-" + mode, extra={"EMS_INIT_ADMIN_PASSWORD": admin, "EMS_INIT_DB_PASSWORD": init_password, "EMS_INIT_DB_USERNAME": state["init_user"], "BOOTSTRAP_DEMO": values.get("BOOTSTRAP_DEMO", "0"), "EMS_INIT_ADMIN_USERNAME": values.get("ADMIN_USER", "admin")})
            state["bootstrap_complete"] = True
            atomic_json(root / "deploy-state.json", state)
        atomic_link(root, "current", release)
        compose.start(timeout)
    except (Exception, KeyboardInterrupt) as exc:
        state.update(status="installation_failed", database_retained=True)
        atomic_json(root / "deploy-state.json", state)
        detail = " " + str(exc) if isinstance(exc, DeployError) else ""
        raise DeployError("Installation stopped. Configuration and any created databases are retained. Correct the cause and use install --resume with the same archive/checksum; incomplete database markers will still be refused." + detail) from None
    atomic_json(root / "deploy-state.json", {"status": "healthy", "current": release.name, "schema_version": manifest["schema_version"], "demo": values.get("BOOTSTRAP_DEMO") == "1"})
    print("Installed " + release.name + ". Administrator: " + values.get("ADMIN_USER", "admin") + ". Password is not saved by the installer.")


def database_name(value):
    if not re.fullmatch(r"[a-z][a-z0-9_]{2,47}", value) or value in {"mysql", "sys", "information_schema", "performance_schema"}:
        raise argparse.ArgumentTypeError("Use a new, non-system database name (3-48 lowercase characters)")
    return value


def parser():
    command = argparse.ArgumentParser(description=__doc__)
    sub = command.add_subparsers(dest="command", required=True)
    for name in ("install", "upgrade", "rollback", "check", "status", "backup", "stop", "start"):
        item = sub.add_parser(name)
        item.add_argument("--root", default="/opt/rent-project")
        item.add_argument("--timeout", type=int, default=360)
        if name in {"install", "upgrade"}:
            item.add_argument("archive", type=Path)
            item.add_argument("--sha256", required=True)
        if name == "rollback":
            item.add_argument("--version")
        if name == "install":
            item.add_argument("--resume", action="store_true", help="Retry only the same recorded, unfinished first installation; reuse its saved configuration")
            item.add_argument("--db-mode", choices=("managed", "external"), default="managed")
            item.add_argument("--db-host")
            item.add_argument("--db-port", type=int, default=3306)
            item.add_argument("--db-user")
            item.add_argument("--init-user")
            item.add_argument("--ssl-ca", type=Path)
            item.add_argument("--platform-db", type=database_name, default="equipment_platform")
            item.add_argument("--alipay-db", type=database_name, default="equipment_alipay")
            item.add_argument("--admin-user", default="admin")
            item.add_argument("--http-bind", default="127.0.0.1")
            item.add_argument("--http-port", type=int, default=8080)
            item.add_argument("--demo", action="store_true")
    return command


def main(argv=None):
    args = parser().parse_args(argv)
    try:
        if not sys.platform.startswith("linux") or sys.version_info < (3, 10):
            raise DeployError("The deployment engine requires Linux and Python 3.10 or later")
        def interrupted(signum, frame):
            raise KeyboardInterrupt()
        signal.signal(signal.SIGTERM, interrupted)
        if not 10 <= args.timeout <= 3600:
            raise DeployError("Health timeout must be between 10 and 3600 seconds")
        if args.command == "install" and (args.platform_db == args.alipay_db or not 1 <= args.http_port <= 65535 or not 1 <= args.db_port <= 65535):
            raise DeployError("Use different databases and valid port numbers")
        root = Path(args.root).expanduser().resolve()
        if root == Path("/"):
            raise DeployError("Filesystem root cannot be an installation directory")
        os.umask(0o077)
        with deployment_lock(root):
            version = subprocess.run(["docker", "compose", "version", "--short"], capture_output=True, text=True)
            match = re.match(r"v?(\d+)\.(\d+)", version.stdout)
            if version.returncode or not match or tuple(map(int, match.groups())) < (2, 20):
                raise DeployError("Docker Compose 2.20 or later is required")
            if args.command in {"install", "upgrade"}:
                release, manifest = stage_release(root, args.archive.resolve(strict=True), args.sha256)
                if args.command == "install":
                    install(root, release, manifest, args)
                else:
                    apply_release(root, release, manifest, args.timeout)
                    print("Upgrade completed: " + release.name)
            elif args.command == "rollback":
                if args.version:
                    if not VERSION_RE.fullmatch(args.version):
                        raise DeployError("Invalid rollback version")
                    release = root / "releases" / args.version
                else:
                    release = selected_release(root, "previous")
                manifest = verify_release(release)
                apply_release(root, release, manifest, args.timeout)
                print("Program rollback completed: " + release.name + ". Database contents were not restored.")
            else:
                release = selected_release(root)
                manifest = verify_release(release)
                compose = Compose(root, release)
                if args.command == "check":
                    compose.run("config", "--quiet")
                    print("Release integrity and Compose configuration are valid: " + manifest["version"])
                elif args.command == "status":
                    result = compose.run("ps", "--format", "json")
                    print("Current release: " + release.name)
                    payload = result.stdout.decode("utf-8")
                    rows = json.loads(payload) if payload.lstrip().startswith("[") else [json.loads(line) for line in payload.splitlines() if line.strip()]
                    for row in rows:
                        print(str(row.get("Service", "unknown")) + ": " + str(row.get("State", "unknown")) + " " + str(row.get("Health", "")))
                elif args.command == "stop":
                    compose.run("stop", *APPLICATIONS)
                    print("Application services stopped. Database, Redis and persistent files are retained.")
                elif args.command == "start":
                    compose.start(args.timeout)
                    print("Application services are healthy. Initialization was not run.")
                elif args.command == "backup":
                    try:
                        compose.run("stop", *APPLICATIONS)
                        target = backup(root, compose)
                    finally:
                        compose.start(args.timeout, recreate=True)
                    print("Backup completed: " + str(target))
        return 0
    except (DeployError, OSError, ValueError, tarfile.TarError, EOFError, KeyboardInterrupt) as exc:
        print(str(exc) if isinstance(exc, DeployError) else "Deployment stopped because a local file, input or archive could not be processed", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
