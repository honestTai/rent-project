"""Offline publication-gate tests; every credential-shaped value is synthetic."""
import contextlib
import hashlib
import io
import json
from pathlib import Path
import struct
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import check_release


class ReleasePublicationTest(unittest.TestCase):
    def setUp(self):
        self.work = tempfile.TemporaryDirectory()
        self.addCleanup(self.work.cleanup)
        self.archive = Path(self.work.name) / 'release.tar.gz'

    def package(self, files=None, *, manifest_edit=None, extra=None, special=None):
        files = {'SOURCE_COMMIT': b'a' * 40 + b'\n', 'README.md': b'Public documentation', **(files or {})}
        manifest = {'format': 1, 'source_commit': 'a' * 40,
                    'files': {name: hashlib.sha256(data).hexdigest() for name, data in files.items()}}
        if manifest_edit:
            manifest_edit(manifest)
        with tarfile.open(self.archive, 'w:gz') as archive:
            for name, data in {**files, 'manifest.json': json.dumps(manifest).encode(), **(extra or {})}.items():
                entry = tarfile.TarInfo(name)
                entry.size = len(data)
                archive.addfile(entry, io.BytesIO(data))
            if special:
                archive.addfile(special)
        return check_release.check_archive(self.archive)

    def jar(self, entries):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w') as jar:
            for name, data in entries.items():
                jar.writestr(name, data)
        return stream.getvalue()

    def kinds(self, findings):
        return {f['type'] for f in findings}

    def test_public_configuration_and_commit_pass(self):
        self.assertEqual([], self.package({'.env.example': b'ADMIN_PASSWORD=\nAPI_TOKEN=${API_TOKEN}\n'}))

    def test_private_runtime_and_legacy_files_fail(self):
        for name in ['.env', '.env.production', 'logs/server.log', 'backup/db.sql',
                     'www/rent/delivery-showcase/customer.png', 'secrets/client.pem']:
            with self.subTest(name=name):
                self.assertIn('private-runtime-or-legacy-file', self.kinds(self.package({name: b'x'})))

    def test_hash_mismatch_and_unlisted_files_fail(self):
        bad = self.package(manifest_edit=lambda m: m['files'].update({'README.md': '0' * 64}), extra={'extra.txt': b'x'})
        self.assertTrue({'file-hash-mismatch', 'unlisted-file'} <= self.kinds(bad))

    def test_missing_manifest_file_and_wrong_commit_fail(self):
        bad = self.package(manifest_edit=lambda m: (m['files'].update({'missing': '0' * 64}), m.update(source_commit='b' * 40)))
        self.assertTrue({'manifest-file-missing', 'source-commit-mismatch'} <= self.kinds(bad))

    def test_traversal_symlinks_and_duplicate_members_fail(self):
        self.assertIn('unsafe-archive-member', self.kinds(self.package({'../escape': b'x'})))
        link = tarfile.TarInfo('link')
        link.type, link.linkname = tarfile.SYMTYPE, 'README.md'
        self.assertIn('unsafe-archive-member', self.kinds(self.package(special=link)))
        duplicate = tarfile.TarInfo('README.md')
        self.assertIn('duplicate-archive-member', self.kinds(self.package(special=duplicate)))

    def test_own_jar_config_is_checked_and_vendor_library_is_not(self):
        marker = ('-----BEGIN ' + 'PRIVATE KEY-----').encode()
        own = self.jar({'BOOT-INF/classes/application.yml': b'password: fixed-deployment-value\n',
                        'BOOT-INF/classes/key.txt': marker})
        findings = self.package({'jars/app.jar': own})
        self.assertTrue({'literal-credential-in-configuration', 'private-key-material'} <= self.kinds(findings))
        vendor = self.jar({'BOOT-INF/lib/vendor.jar': self.jar({'vendor-test.txt': marker})})
        self.assertEqual([], self.package({'jars/app.jar': vendor}))

    def test_jvm_constant_pool_strings_are_checked(self):
        marker = ('gh' + 'p_' + 'A' * 36).encode()
        class_file = b'\xca\xfe\xba\xbe\x00\x00\x00\x34' + struct.pack('>H', 2) + b'\x01' + struct.pack('>H', len(marker)) + marker
        jar = self.jar({'BOOT-INF/classes/com/example/App.class': class_file})
        self.assertIn('github-credential', self.kinds(self.package({'jars/app.jar': jar})))

    def test_command_output_never_contains_detected_value(self):
        marker = ('gh' + 'p_' + 'B' * 36).encode()
        self.package({'config.txt': marker})
        output = io.StringIO()
        with patch('sys.argv', ['check_release.py', str(self.archive)]), contextlib.redirect_stdout(output):
            self.assertEqual(1, check_release.main())
        self.assertNotIn(marker.decode(), output.getvalue())
        self.assertIn('github-credential', output.getvalue())


if __name__ == '__main__':
    unittest.main()
