"""Publication audit fails closed and never retains raw scanner findings."""
import hashlib
import io
import json
from pathlib import Path
import struct
import subprocess
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import audit_release


class ReleaseAuditTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.archive = self.directory / 'release.tar.gz'
        self.report = self.directory / 'report.json'
        self.binary = self.directory / 'gitleaks'
        self.export_directory = None

    def package(self, additions=None):
        files = {'SOURCE_COMMIT': b'a' * 40 + b'\n', **(additions or {})}
        manifest = {'format': 1, 'version': 'v1.0.0', 'source_commit': 'a' * 40,
                    'files': {name: hashlib.sha256(content).hexdigest() for name, content in files.items()}}
        with tarfile.open(self.archive, 'w:gz') as packed:
            for name, data in {**files, 'manifest.json': json.dumps(manifest).encode()}.items():
                entry = tarfile.TarInfo(name)
                entry.size = len(data)
                packed.addfile(entry, io.BytesIO(data))

    def jar(self, entries):
        data = io.BytesIO()
        with zipfile.ZipFile(data, 'w') as packed:
            for name, content in entries.items():
                packed.writestr(name, content)
        return data.getvalue()

    def scanner(self, args, **kwargs):
        if args[1] == 'version':
            return subprocess.CompletedProcess(args, 0, '8.30.1\n', '')
        self.export_directory = Path(args[2])
        self.assertIn('--ignore-gitleaks-allow', args)
        self.assertIn('--redact=100', args)
        self.assertEqual(Path(args[args.index('--gitleaks-ignore-path') + 1]).read_text(), '')
        Path(args[args.index('--report-path') + 1]).write_text('[]')
        return subprocess.CompletedProcess(args, 0, b'', b'')

    def test_own_common_classes_are_exported_and_temporary_text_is_removed(self):
        value = b'ordinary-public-test-constant'
        clazz = b'\xca\xfe\xba\xbe\x00\x00\x00\x34' + struct.pack('>H', 2) + b'\x01' + struct.pack('>H', len(value)) + value
        common = self.jar({'com/common/Config.class': clazz})
        self.package({'jars/app.jar': self.jar({'BOOT-INF/lib/equipment-common-1.0.jar': common})})

        def scan(args, **kwargs):
            result = self.scanner(args, **kwargs)
            if args[1] == 'dir':
                exported = self.export_directory / 'jars/app.jar/BOOT-INF/lib/equipment-common-1.0.jar/com/common/Config.class.txt'
                self.assertEqual(exported.read_bytes(), value)
            return result

        with patch.object(audit_release.subprocess, 'run', side_effect=scan):
            report = audit_release.audit_package(self.archive, self.binary, self.report)
        self.assertTrue(report['passed'])
        self.assertEqual(report['counts']['application_classes'], 1)
        self.assertEqual(report['counts']['own_nested_libraries'], 1)
        self.assertFalse(self.export_directory.exists())

    def test_candidate_report_contains_only_rule_path_and_line(self):
        self.package()
        raw = 'synthetic-value-that-must-never-be-reported'

        def scan(args, **kwargs):
            result = self.scanner(args, **kwargs)
            if args[1] == 'dir':
                data = [{'File': str(self.export_directory / 'SOURCE_COMMIT'), 'RuleID': 'test-rule',
                         'StartLine': 1, 'Secret': raw, 'Match': raw}]
                Path(args[args.index('--report-path') + 1]).write_text(json.dumps(data))
                result.returncode = 1
            return result

        with patch.object(audit_release.subprocess, 'run', side_effect=scan):
            with self.assertRaises(audit_release.ReleaseAuditError):
                audit_release.audit_package(self.archive, self.binary, self.report)
        self.assertNotIn(raw, self.report.read_text())
        findings = json.loads(self.report.read_text())['findings']
        self.assertEqual(findings, [{'path': 'SOURCE_COMMIT', 'type': 'gitleaks-candidate', 'rule': 'test-rule', 'line': 1}])
        self.assertFalse(self.export_directory.exists())

    def test_gate_findings_stop_before_external_scanner(self):
        self.package({'.env': b'private runtime configuration'})
        with patch.object(audit_release.subprocess, 'run') as scanner:
            with self.assertRaises(audit_release.ReleaseAuditError):
                audit_release.audit_package(self.archive, self.binary, self.report)
        scanner.assert_not_called()
        self.assertFalse(json.loads(self.report.read_text())['passed'])

    def test_wrong_tool_version_fails_without_captured_output(self):
        self.package()
        output = 'synthetic-sensitive-diagnostic'
        with patch.object(audit_release.subprocess, 'run', return_value=subprocess.CompletedProcess([], 1, output, output)):
            with self.assertRaises(audit_release.ReleaseAuditError):
                audit_release.audit_package(self.archive, self.binary, self.report)
        self.assertNotIn(output, self.report.read_text())
        self.assertEqual(json.loads(self.report.read_text())['findings'][0]['type'], 'audit-could-not-complete')


if __name__ == '__main__':
    unittest.main()
