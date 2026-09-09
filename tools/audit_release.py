#!/usr/bin/env python3
"""Audit a built release and retain only a report without credential values."""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import tarfile
import tempfile
import zipfile

from check_release import check_archive, class_strings, safe_name

GITLEAKS_VERSION = '8.30.1'


class ReleaseAuditError(ValueError):
    """The sanitized report has been written, but publication must stop."""


def audit_package(archive: Path, gitleaks_binary: Path, report_path: Path) -> dict:
    """Return a passing report, or write a failing report and raise ReleaseAuditError.

    No Secret, Match, source text, configuration values or tool output is retained
    in the report. Raw scan inputs and Gitleaks output live only in a temporary
    directory. The caller must verify the official Gitleaks binary's checksum.
    """
    archive, gitleaks_binary, report_path = Path(archive), Path(gitleaks_binary), Path(report_path)
    counts = {'package_files': 0, 'manifest_files': 0, 'exported_text_files': 0,
              'application_classes': 0, 'application_resources': 0,
              'own_nested_libraries': 0, 'standalone_classes': 0}
    report = {'format': 1, 'passed': False, 'package': archive.name,
              'gitleaks_version': GITLEAKS_VERSION, 'counts': counts, 'findings': []}
    try:
        with archive.open('rb') as stream:
            report['sha256'] = hashlib.file_digest(stream, 'sha256').hexdigest()
        report['findings'] = check_archive(archive)
        if not report['findings']:
            with tempfile.TemporaryDirectory(prefix='rent-release-audit-') as temporary:
                directory = Path(temporary)
                texts = directory / 'texts'
                texts.mkdir()

                def write_text(label: str, data: bytes):
                    if not safe_name(label):
                        raise ValueError('Invalid application resource path')
                    path = texts / label
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(data)
                    counts['exported_text_files'] += 1

                def inspect_jar(label: str, data: bytes, *, common: bool = False):
                    with zipfile.ZipFile(io.BytesIO(data)) as packed:
                        for entry in packed.infolist():
                            if entry.is_dir():
                                continue
                            if not common and re.fullmatch(r'BOOT-INF/lib/equipment-common-[^/]+\.jar', entry.filename):
                                counts['own_nested_libraries'] += 1
                                inspect_jar(label + '/' + entry.filename, packed.read(entry), common=True)
                                continue
                            if not common and not entry.filename.startswith('BOOT-INF/classes/'):
                                continue
                            content = packed.read(entry)
                            nested = label + '/' + entry.filename
                            if entry.filename.endswith('.class'):
                                write_text(nested + '.txt', class_strings(content))
                                counts['application_classes'] += 1
                            elif b'\0' not in content[:8192]:
                                write_text(nested, content)
                                counts['application_resources'] += 1

                with tarfile.open(archive, 'r:*') as packed:
                    for entry in packed:
                        if not entry.isfile():
                            continue
                        counts['package_files'] += 1
                        content = packed.extractfile(entry).read()
                        if entry.name == 'manifest.json':
                            manifest = json.loads(content)
                            version = manifest.get('version', '')
                            if not isinstance(version, str) or not re.fullmatch(r'v\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?', version):
                                raise ValueError('Invalid release version')
                            report.update(version=version, source_commit=manifest['source_commit'])
                            counts['manifest_files'] = len(manifest['files'])
                        if entry.name.endswith('.jar'):
                            inspect_jar(entry.name, content)
                        elif entry.name.endswith('.class'):
                            write_text(entry.name + '.txt', class_strings(content))
                            counts['standalone_classes'] += 1
                        elif b'\0' not in content[:8192]:
                            try:
                                content.decode('utf-8')
                            except UnicodeError:
                                continue
                            write_text(entry.name, content)

                version_result = subprocess.run([str(gitleaks_binary.resolve()), 'version'], capture_output=True, text=True, timeout=30)
                if version_result.returncode or version_result.stdout.strip() != GITLEAKS_VERSION:
                    raise ValueError('Unexpected Gitleaks version')
                config = directory / 'standard.toml'
                config.write_text('[extend]\nuseDefault = true\n', encoding='utf-8')
                ignore = directory / 'empty.gitleaksignore'
                ignore.write_text('', encoding='utf-8')
                result_path = directory / 'gitleaks.json'
                result = subprocess.run([
                    str(gitleaks_binary.resolve()), 'dir', str(texts), '--config', str(config),
                    '--gitleaks-ignore-path', str(ignore), '--ignore-gitleaks-allow',
                    '--max-decode-depth', '3', '--max-archive-depth', '3', '--redact=100',
                    '--no-banner', '--no-color', '--report-format', 'json', '--report-path', str(result_path),
                ], capture_output=True, timeout=600)
                if result.returncode not in (0, 1):
                    raise ValueError('Gitleaks did not complete')
                results = json.loads(result_path.read_text(encoding='utf-8-sig'))
                if not isinstance(results, list) or bool(results) != (result.returncode == 1):
                    raise ValueError('Inconsistent Gitleaks result')
                for item in results:
                    path = Path(item['File']).resolve().relative_to(texts.resolve()).as_posix()
                    rule = item['RuleID']
                    if not isinstance(rule, str) or not re.fullmatch(r'[A-Za-z0-9_-]+', rule):
                        raise ValueError('Invalid Gitleaks rule identifier')
                    finding = {'path': path, 'type': 'gitleaks-candidate', 'rule': rule}
                    if isinstance(item.get('StartLine'), int) and item['StartLine'] > 0:
                        finding['line'] = item['StartLine']
                    report['findings'].append(finding)
                report['passed'] = not report['findings']
    except Exception:
        # Never include an exception message or captured output; either may contain
        # values from malformed configuration or a tool's diagnostic output.
        report['findings'].append({'path': archive.name, 'type': 'audit-could-not-complete'})
        report['passed'] = False
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    if not report['passed']:
        raise ReleaseAuditError('Release audit failed; inspect the sanitized report before publication') from None
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--gitleaks-binary', required=True, type=Path)
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    try:
        audit_package(args.archive, args.gitleaks_binary, args.report)
    except ReleaseAuditError:
        print('Release audit failed. Review the report containing only paths and finding types.')
        return 1
    print('Release audit passed. The report contains hashes, counts and no credential values.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
