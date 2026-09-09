#!/usr/bin/env python3
"""Check release contents without extracting files or printing credential values.

This is a publication gate for accidental disclosures, not a vulnerability audit.
Third-party JAR libraries are excluded from credential heuristics; application
resources and application class constants are inspected inside Spring Boot JARs.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
import struct
import tarfile
import zipfile

MAX_FILE_BYTES = 512 * 1024 * 1024
PRIVATE_PARTS = {'.git', '.agents', '.artifacts', '__pycache__', 'node_modules',
                 'backups', 'backup', 'logs', 'uploads', '.agent-data', 'delivery-showcase'}
PRIVATE_EXTENSIONS = {'.pem', '.key', '.p12', '.pfx', '.jks', '.keystore', '.sqlite', '.sqlite3', '.db', '.log'}
CONFIG_EXTENSIONS = {'.yml', '.yaml', '.properties', '.json', '.env', '.example'}
TOKEN_RULES = {
    'private-key-material': re.compile(r'-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----'),
    'github-credential': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b'),
    'cloud-access-key': re.compile(r'\b(?:AKIA[A-Z0-9]{16}|LTAI[A-Za-z0-9]{16,}|AIza[A-Za-z0-9_-]{35})\b'),
    'service-api-token': re.compile(r'\b(?:sk-(?:proj-|ant-api\d+-)?[A-Za-z0-9_-]{24,}|xox[baprs]-[A-Za-z0-9-]{20,}|sk_live_[A-Za-z0-9]{20,})\b'),
    'embedded-jwt': re.compile(r'\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b'),
    'credential-in-url': re.compile(r'\b(?:https?|mysql|redis|postgres(?:ql)?|mongodb(?:\+srv)?|amqp)://[^\s/"<>]+:[^\s/"<>]+@', re.I),
}
ASSIGNMENT = re.compile(
    r'''(?im)^[ \t]*["']?([\w.-]*(?:password|passwd|passphrase|secret|token|private_?key|access_?key|api_?key)[\w.-]*)["']?[ \t]*[:=][ \t]*([^\r\n]+)''')


def safe_name(name: str) -> bool:
    parts = PurePosixPath(name).parts
    return bool(name and '\\' not in name and '\x00' not in name and not name.startswith('/')
                and not re.match(r'^[A-Za-z]:', name) and '..' not in parts and '.' not in parts)


def forbidden_name(name: str) -> bool:
    path = PurePosixPath(name)
    leaf = path.name.lower()
    return (bool(set(p.lower() for p in path.parts) & PRIVATE_PARTS)
            or leaf.startswith('.env') and leaf != '.env.example'
            or path.suffix.lower() in PRIVATE_EXTENSIONS
            or bool(re.search(r'(?:^|[-_.])(dump|backup)(?:[-_.]|$).*\.(?:sql|gz|zip|bak)$', leaf))
            or leaf.endswith(('.sql.gz', '.sql.bak', '.sqlite3-wal', '.sqlite3-shm')))


def placeholder(value: str) -> bool:
    value = value.split(' #', 1)[0].strip().rstrip(',;').strip().strip('"\'')
    if value.lower() in {'', 'null', 'none', 'true', 'false', 'string', 'password', 'token', 'secret'}:
        return True
    return (value.startswith(('${', '@', '<', 'your-', 'YOUR_', 'example-', 'REPLACE_'))
            or value in {'{}', '[]', '~'})


def text_findings(name: str, data: bytes, *, config: bool = False) -> list[dict]:
    text = data.decode('utf-8', errors='replace')
    findings = [{'path': name, 'type': label} for label, pattern in TOKEN_RULES.items() if pattern.search(text)]
    if config:
        for match in ASSIGNMENT.finditer(text):
            key, value = match.groups()
            # These keys describe metadata/parameter names, not credential values.
            if key.lower().endswith(('name', 'parameter', 'header', 'timeout', 'ttl', 'enabled', 'length', 'type')):
                continue
            if not placeholder(value):
                findings.append({'path': name, 'type': 'literal-credential-in-configuration'})
                break
    return findings


def class_strings(data: bytes) -> bytes:
    """Read JVM UTF-8 constants rather than interpreting an entire .class as text."""
    if len(data) < 10 or data[:4] != b'\xca\xfe\xba\xbe':
        return b''
    count = struct.unpack_from('>H', data, 8)[0]
    pos, index, values = 10, 1, []
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
             11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            length = struct.unpack_from('>H', data, pos)[0]
            pos += 2
            values.append(data[pos:pos + length])
            pos += length
        elif tag in sizes:
            pos += sizes[tag]
            if tag in (5, 6):
                index += 1
        else:
            raise ValueError('Invalid JVM constant pool')
        if pos > len(data):
            raise ValueError('Truncated JVM constant pool')
        index += 1
    return b'\n'.join(values)


def jar_findings(name: str, data: bytes, *, application_library: bool = False) -> list[dict]:
    """Inspect Spring Boot application files, or an explicitly selected own library.

    The only nested dependency selected automatically is equipment-common. Other
    BOOT-INF/lib dependencies may contain vendor test keys and stay out of scope.
    """
    findings = []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            label = name + '!/' + entry.filename
            own_nested = (not application_library and
                          re.fullmatch(r'BOOT-INF/lib/equipment-common-[^/]+\.jar', entry.filename) is not None)
            if not application_library and not own_nested and not entry.filename.startswith('BOOT-INF/classes/'):
                continue
            if not safe_name(entry.filename) or forbidden_name(entry.filename):
                findings.append({'path': label, 'type': 'private-or-unsafe-application-resource'})
                continue
            if entry.file_size > MAX_FILE_BYTES:
                findings.append({'path': label, 'type': 'oversized-application-resource'})
                continue
            content = archive.read(entry)
            if own_nested:
                findings.extend(jar_findings(label, content, application_library=True))
                continue
            if entry.filename.endswith('.class'):
                content = class_strings(content)
            elif b'\0' in content[:8192]:
                continue
            findings.extend(text_findings(label, content, config=PurePosixPath(entry.filename).suffix.lower() in CONFIG_EXTENSIONS))
    return findings


def check_archive(path: Path) -> list[dict]:
    findings = []
    with tarfile.open(path, 'r:*') as archive:
        members = archive.getmembers()
        files = {}
        for member in members:
            if not safe_name(member.name) or not (member.isfile() or member.isdir()):
                findings.append({'path': member.name, 'type': 'unsafe-archive-member'})
                continue
            if member.isdir():
                continue
            if member.name in files:
                findings.append({'path': member.name, 'type': 'duplicate-archive-member'})
            files[member.name] = member
            if forbidden_name(member.name):
                findings.append({'path': member.name, 'type': 'private-runtime-or-legacy-file'})
            if member.size > MAX_FILE_BYTES:
                findings.append({'path': member.name, 'type': 'oversized-archive-member'})
        if findings:
            return findings
        if 'manifest.json' not in files or files['manifest.json'].size > 10 * 1024 * 1024:
            return [{'path': 'manifest.json', 'type': 'missing-or-invalid-manifest'}]
        manifest = json.load(archive.extractfile(files['manifest.json']))
        expected = manifest.get('files') if isinstance(manifest, dict) else None
        if not isinstance(expected, dict) or manifest.get('format') != 1:
            return [{'path': 'manifest.json', 'type': 'invalid-manifest-format'}]
        for name in set(files) - {'manifest.json'} - set(expected):
            findings.append({'path': name, 'type': 'unlisted-file'})
        for name in set(expected) - (set(files) - {'manifest.json'}):
            findings.append({'path': str(name), 'type': 'manifest-file-missing'})
        for name, member in files.items():
            if name == 'manifest.json':
                continue
            content = archive.extractfile(member).read()
            wanted = expected.get(name)
            if not isinstance(wanted, str) or not re.fullmatch(r'[a-f0-9]{64}', wanted) or hashlib.sha256(content).hexdigest() != wanted:
                findings.append({'path': name, 'type': 'file-hash-mismatch'})
            if name == 'SOURCE_COMMIT' and content.decode('ascii', errors='replace').strip() != manifest.get('source_commit'):
                findings.append({'path': name, 'type': 'source-commit-mismatch'})
            if name.endswith('.jar'):
                findings.extend(jar_findings(name, content))
            elif b'\0' not in content[:8192]:
                findings.extend(text_findings(name, content, config=PurePosixPath(name).suffix.lower() in CONFIG_EXTENSIONS))
        if 'SOURCE_COMMIT' not in files or not re.fullmatch(r'[a-f0-9]{40}', str(manifest.get('source_commit', ''))):
            findings.append({'path': 'SOURCE_COMMIT', 'type': 'missing-or-invalid-source-commit'})
    return [{'path': name, 'type': kind} for name, kind in sorted({(item['path'], item['type']) for item in findings})]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    args = parser.parse_args()
    try:
        findings = check_archive(args.archive)
    except (OSError, ValueError, KeyError, TypeError, IndexError, struct.error, tarfile.TarError, zipfile.BadZipFile):
        print(json.dumps({'path': args.archive.name, 'type': 'unreadable-or-malformed-release'}))
        return 1
    for finding in findings:
        print(json.dumps(finding, ensure_ascii=False))
    print(f'Release publication check: {len(findings)} finding(s). Values are never printed.')
    return int(bool(findings))


if __name__ == '__main__':
    raise SystemExit(main())
