#!/usr/bin/env python3
"""Offline release-engine regression tests. No Docker or database is contacted."""
import hashlib
import io
import json
import os
from pathlib import Path
import sys
import subprocess
import tarfile
import tempfile
import unittest
from unittest import mock

import deploy


def release_archive(directory, version="v1.0.0", schema="test-schema", extra=None, alter=None):
    files = {"docker-compose.yml": b"services: {}\n", "scripts/initialize.py": b"# fixture only\n", "sql/init.sql": b"-- empty fixture\n", "sql/seed-platform.sql": b"", "sql/seed-alipay.sql": b"", "sql/demo.sql": b"", "www/platform/index.html": b"platform", "www/rent/index.html": b"rent"}
    files.update({f"jars/{name}.jar": b"fake-test-artifact" for name in deploy.APPLICATIONS if name != "nginx"})
    manifest = {"format": 1, "version": version, "schema_version": schema, "files": {name: hashlib.sha256(data).hexdigest() for name, data in files.items()}}
    if alter:
        alter(manifest, files)
    archive = directory / (version + ".tar.gz")
    with tarfile.open(archive, "w:gz") as output:
        for name, data in {"manifest.json": json.dumps(manifest).encode(), **files}.items():
            entry = tarfile.TarInfo(name)
            entry.size = len(data)
            output.addfile(entry, io.BytesIO(data))
        if extra:
            for entry, data in extra:
                output.addfile(entry, io.BytesIO(data) if data is not None else None)
    return archive, deploy.sha256(archive)


class ArchiveTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name)
        self.root = self.path / "installation"
        self.root.mkdir()

    def tearDown(self):
        self.temp.cleanup()

    def test_verified_archive_and_identical_restage(self):
        archive, checksum = release_archive(self.path)
        release, manifest = deploy.stage_release(self.root, archive, checksum)
        self.assertEqual(deploy.verify_release(release), manifest)
        self.assertEqual(deploy.stage_release(self.root, archive, checksum)[0], release)

    def test_wrong_whole_archive_hash_has_no_extraction(self):
        archive, _ = release_archive(self.path)
        with self.assertRaises(deploy.DeployError):
            deploy.stage_release(self.root, archive, "0" * 64)
        self.assertEqual(list(self.root.iterdir()), [])

    def test_manifest_file_tampering_removes_only_staging(self):
        def tamper(manifest, files):
            files["jars/equipment-platform.jar"] = b"tampered"
        archive, checksum = release_archive(self.path, alter=tamper)
        unrelated = self.root / "keep.txt"
        unrelated.write_text("retain")
        with self.assertRaises(deploy.DeployError):
            deploy.stage_release(self.root, archive, checksum)
        self.assertEqual(unrelated.read_text(), "retain")
        self.assertEqual(list((self.root / "releases").iterdir()), [])

    def test_traversal_absolute_windows_paths_and_private_env_rejected(self):
        for unsafe in ("../escaped", "/tmp/escaped", "C:/escaped", "a\\escaped", ".env", "shared/password", "a/../../escaped"):
            with self.subTest(path=unsafe):
                entry = tarfile.TarInfo(unsafe)
                entry.size = 1
                archive, checksum = release_archive(self.path, extra=[(entry, b"x")])
                with self.assertRaises(deploy.DeployError):
                    deploy.stage_release(self.root, archive, checksum)
        self.assertFalse((self.path / "escaped").exists())

    def test_links_devices_duplicate_and_unlisted_files_rejected(self):
        entries = []
        for kind in (tarfile.SYMTYPE, tarfile.LNKTYPE, tarfile.CHRTYPE, tarfile.FIFOTYPE):
            entry = tarfile.TarInfo("malicious")
            entry.type, entry.linkname = kind, "../outside"
            entries.append((entry, None))
        for name in ("unlisted", "./www/rent/index.html"):
            entry = tarfile.TarInfo(name)
            entry.size = 1
            entries.append((entry, b"x"))
        for entry in entries:
            with self.subTest(type=entry[0].type, name=entry[0].name):
                archive, checksum = release_archive(self.path, extra=[entry])
                with self.assertRaises(deploy.DeployError):
                    deploy.stage_release(self.root, archive, checksum)

    def test_same_version_different_archive_cannot_replace_program(self):
        archive, checksum = release_archive(self.path)
        release, _ = deploy.stage_release(self.root, archive, checksum)
        original = (release / "www/rent/index.html").read_bytes()
        def change(manifest, files):
            files["www/rent/index.html"] = b"changed"
            manifest["files"]["www/rent/index.html"] = hashlib.sha256(b"changed").hexdigest()
        archive, checksum = release_archive(self.path, alter=change)
        with self.assertRaises(deploy.DeployError):
            deploy.stage_release(self.root, archive, checksum)
        self.assertEqual((release / "www/rent/index.html").read_bytes(), original)

    def test_installed_file_tampering_rejected(self):
        archive, checksum = release_archive(self.path)
        release, _ = deploy.stage_release(self.root, archive, checksum)
        (release / "www/rent/index.html").write_text("modified")
        with self.assertRaises(deploy.DeployError):
            deploy.verify_release(release)

    def test_environment_secret_roundtrip_and_no_overwrite(self):
        path = self.root / ".env"
        values = {"PASSWORD": 'fixture-$quoted"\\value! with spaces', "DB_MODE": "external"}
        deploy.write_env(path, values)
        self.assertEqual(deploy.read_env(path), values)
        snapshot = path.read_bytes()
        with self.assertRaises(deploy.DeployError):
            deploy.write_env(path, {"PASSWORD": "reset"})
        self.assertEqual(path.read_bytes(), snapshot)

    def test_existing_environment_prevents_initializer(self):
        (self.root / ".env").write_text("existing")
        with mock.patch.object(deploy, "Compose") as compose:
            with self.assertRaises(deploy.DeployError):
                deploy.install(self.root, self.path, {}, None)
            compose.assert_not_called()

    def test_failure_diagnostics_are_private_not_in_exception(self):
        deploy.write_env(self.root / ".env", {"PROJECT_NAME": "unit_test", "DB_MODE": "managed", "RDS_PASSWORD": "fixture-confidential"})
        compose = deploy.Compose(self.root, self.path)
        failure = subprocess.CompletedProcess([], 1, b"fixture-confidential output", b"private driver failure")
        with mock.patch.object(deploy.subprocess, "run", return_value=failure):
            with self.assertRaises(deploy.DeployError) as caught:
                compose.run("build", "bootstrap-managed")
        self.assertNotIn("fixture-confidential", str(caught.exception))
        self.assertIn("deploy-private.log", str(caught.exception))
        self.assertIn("fixture-confidential", (self.root / "deploy-private.log").read_text())
        if sys.platform.startswith("linux"):
            self.assertEqual((self.root / "deploy-private.log").stat().st_mode & 0o777, 0o600)

    def test_resume_cannot_initialize_a_healthy_installation(self):
        deploy.atomic_json(self.root / "deploy-state.json", {"status": "healthy", "release": "v1.0.0"})
        args = deploy.parser().parse_args(["install", "fixture.tar.gz", "--sha256", "0" * 64, "--resume"])
        with mock.patch.object(deploy, "Compose") as compose:
            with self.assertRaisesRegex(deploy.DeployError, "unfinished first installation"):
                deploy.resume_install(self.root, self.root / "v1.0.0", {}, args)
            compose.assert_not_called()


@unittest.skipUnless(sys.platform.startswith("linux"), "Linux symlink/flock deployment contract")
class SwitchTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name)
        self.root = self.path / "installation"
        self.root.mkdir()
        old_archive, old_hash = release_archive(self.path)
        self.old, _ = deploy.stage_release(self.root, old_archive, old_hash)
        new_archive, new_hash = release_archive(self.path, version="v1.0.1")
        self.new, self.manifest = deploy.stage_release(self.root, new_archive, new_hash)
        deploy.atomic_link(self.root, "current", self.old)
        deploy.write_env(self.root / ".env", {"PASSWORD": "fixture-never-reset", "DB_MODE": "managed"})
        for path in ("mysql/data", "redis/aof", "uploads/customer-file", "logs/app.log"):
            item = self.root / "shared" / path
            item.parent.mkdir(parents=True, exist_ok=True)
            item.write_text("retained")
        self.before = {str(path.relative_to(self.root)): path.read_bytes() for path in (self.root / "shared").rglob("*") if path.is_file()}
        self.env_before = (self.root / ".env").read_bytes()
        self.calls = []
        self.fail_start = set()
        self.fail_backup = False
        owner = self
        class FakeCompose:
            def __init__(self, root, release):
                self.release = release
            def prepare(self, bootstrap=False):
                owner.calls.append((self.release.name, "prepare", bootstrap))
            def run(self, *args, **kwargs):
                owner.calls.append((self.release.name, *args))
            def start(self, timeout, recreate=False):
                owner.calls.append((self.release.name, "start", recreate))
                if self.release.name in owner.fail_start:
                    raise deploy.DeployError("fixture health failure")
        self.factory = FakeCompose

    def tearDown(self):
        self.temp.cleanup()

    def fake_backup(self, root, compose):
        self.calls.append((compose.release.name, "backup"))
        if self.fail_backup:
            raise deploy.DeployError("fixture backup failure")
        return root / "backups" / "fixture"

    def apply(self, release=None, manifest=None):
        deploy.apply_release(self.root, release or self.new, manifest or self.manifest, 10, self.factory, self.fake_backup)

    def assert_preserved(self):
        self.assertEqual((self.root / ".env").read_bytes(), self.env_before)
        for name, data in self.before.items():
            self.assertEqual((self.root / name).read_bytes(), data)
        self.assertFalse(any("bootstrap" in str(call) or "initialize" in str(call) for call in self.calls))

    def test_upgrade_preserves_configuration_data_and_old_release(self):
        self.apply()
        self.assertEqual(deploy.selected_release(self.root), self.new)
        self.assertEqual(deploy.selected_release(self.root, "previous"), self.old)
        self.assertTrue(self.old.is_dir())
        self.assert_preserved()

    def test_health_failure_restores_previous_program(self):
        self.fail_start.add(self.new.name)
        with self.assertRaisesRegex(deploy.DeployError, "previous release has been restored"):
            self.apply()
        self.assertEqual(deploy.selected_release(self.root), self.old)
        self.assertIn((self.old.name, "start", True), self.calls)
        self.assertEqual(json.loads((self.root / "deploy-state.json").read_text())["status"], "rolled_back_after_failure")
        self.assert_preserved()

    def test_backup_failure_restarts_previous_without_switching(self):
        self.fail_backup = True
        with self.assertRaises(deploy.DeployError):
            self.apply()
        self.assertEqual(deploy.selected_release(self.root), self.old)
        self.assertNotIn((self.new.name, "start", True), self.calls)
        self.assertIn((self.old.name, "start", True), self.calls)
        self.assert_preserved()

    def test_failed_recovery_is_reported_honestly(self):
        self.fail_start.update((self.old.name, self.new.name))
        with self.assertRaisesRegex(deploy.DeployError, "did not recover"):
            self.apply()
        self.assertEqual(json.loads((self.root / "deploy-state.json").read_text())["status"], "recovery_required")
        self.assertEqual(deploy.selected_release(self.root), self.old)
        self.assert_preserved()

    def test_schema_change_rejected_before_docker(self):
        manifest = dict(self.manifest, schema_version="incompatible")
        with self.assertRaisesRegex(deploy.DeployError, "schema versions differ"):
            self.apply(manifest=manifest)
        self.assertEqual(self.calls, [])
        self.assert_preserved()

    def test_explicit_rollback_preserves_database_and_previous_release(self):
        self.apply()
        self.apply(self.old, deploy.verify_release(self.old))
        self.assertEqual(deploy.selected_release(self.root), self.old)
        self.assertEqual(deploy.selected_release(self.root, "previous"), self.new)
        self.assert_preserved()

    def test_deployment_lock_excludes_another_operation(self):
        with deploy.deployment_lock(self.root):
            with self.assertRaises(deploy.DeployError):
                with deploy.deployment_lock(self.root):
                    self.fail("second lock was accepted")

    def test_resume_after_bootstrap_does_not_initialize_or_ask_for_password(self):
        deploy.atomic_json(self.root / "deploy-state.json", {"status": "installation_failed", "release": self.old.name, "bootstrap_complete": True, "init_user": "root"})
        args = deploy.parser().parse_args(["install", "fixture.tar.gz", "--sha256", "0" * 64, "--resume"])
        fake = mock.Mock()
        fake.values = {"DB_MODE": "managed", "ADMIN_USER": "admin", "BOOTSTRAP_DEMO": "0"}
        with mock.patch.object(deploy, "Compose", return_value=fake), mock.patch.object(deploy, "ask_secret") as secret:
            deploy.resume_install(self.root, self.old, deploy.verify_release(self.old), args)
            secret.assert_not_called()
        self.assertEqual(fake.prepare.call_args, mock.call(bootstrap=False))
        self.assertFalse(any("bootstrap" in str(call) for call in fake.run.call_args_list))
        self.assertEqual((self.root / ".env").read_bytes(), self.env_before)
        self.assertEqual(json.loads((self.root / "deploy-state.json").read_text())["status"], "healthy")

    def test_resume_before_bootstrap_reuses_all_generated_secrets(self):
        values = deploy.read_env(self.root / ".env")
        values.update(MYSQL_ROOT_PASSWORD="fixture-root-password", ADMIN_USER="chosen_admin", BOOTSTRAP_DEMO="1")
        (self.root / ".env").unlink()
        deploy.write_env(self.root / ".env", values)
        original = (self.root / ".env").read_bytes()
        deploy.atomic_json(self.root / "deploy-state.json", {"status": "installation_failed", "release": self.old.name, "bootstrap_complete": False, "init_user": "root"})
        fake = mock.Mock()
        fake.values = values
        args = deploy.parser().parse_args(["install", "fixture.tar.gz", "--sha256", "0" * 64, "--resume"])
        with mock.patch.object(deploy, "Compose", return_value=fake), mock.patch.object(deploy, "ask_secret", return_value="fixture-admin-password!"):
            deploy.resume_install(self.root, self.old, deploy.verify_release(self.old), args)
        bootstrap = [call for call in fake.run.call_args_list if "bootstrap-managed" in call.args]
        self.assertEqual(len(bootstrap), 1)
        self.assertEqual(bootstrap[0].kwargs["extra"]["EMS_INIT_DB_PASSWORD"], values["MYSQL_ROOT_PASSWORD"])
        self.assertEqual(bootstrap[0].kwargs["extra"]["EMS_INIT_ADMIN_USERNAME"], "chosen_admin")
        self.assertEqual((self.root / ".env").read_bytes(), original)


if __name__ == "__main__":
    unittest.main()
