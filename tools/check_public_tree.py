"""Check the Git publication file set without printing matched secret values."""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
names = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], cwd=ROOT).decode('utf-8').split('\0')
problems = []
patterns = {
    'private-key material': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'GitHub credential': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b'),
    'cloud access-key identifier': re.compile(r'\b(?:AKIA[A-Z0-9]{16}|LTAI[A-Za-z0-9]{16,})\b'),
    'personal webhook URL': re.compile(r'https://(?:open\.feishu\.cn|open\.larksuite\.com)/open-apis/bot/v2/hook/[a-f0-9-]{20,}'),
}
for name in sorted(set(filter(None, names))):
    path = ROOT / name
    if not path.is_file():
        continue
    if name.startswith(('.agents/', '.artifacts/', 'docs/generated/')) or path.name == '.env':
        problems.append((name, 0, 'private file in publication set'))
    if path.suffix.lower() in {'.docx', '.xlsx', '.sqlite3', '.pem', '.key', '.p12', '.pfx'}:
        problems.append((name, 0, 'unreviewed document or credential file'))
    if path.stat().st_size > 30 * 1024 * 1024:
        problems.append((name, 0, 'unexpected large file'))
    if path.suffix.lower() in {'.png', '.jpg', '.jpeg', '.ttc', '.ttf', '.woff', '.woff2', '.ico', '.webp'}:
        continue
    try:
        source = path.read_text(encoding='utf-8')
    except UnicodeError:
        continue
    for label, pattern in patterns.items():
        for match in pattern.finditer(source):
            problems.append((name, source[:match.start()].count('\n') + 1, label))
for name, line, reason in problems:
    print(f'{name}:{line}: {reason}')
print(f'Checked {len(set(filter(None, names)))} publication files; {len(problems)} findings. Image content and business context still require review.')
sys.exit(bool(problems))
