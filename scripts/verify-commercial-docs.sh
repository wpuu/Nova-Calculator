#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

required = [
    Path('README.md'),
    Path('LICENSE'),
    Path('NOTICE'),
    Path('docs/PLAY_DATA_SAFETY_BASELINE.md'),
    Path('docs/PRIVACY_POLICY_DRAFT.md'),
]
for path in required:
    if not path.is_file() or path.stat().st_size == 0:
        raise SystemExit(f'Nova commercial docs guard: missing required file {path}')

readme = Path('README.md').read_text(encoding='utf-8')
for forbidden in [
    'Secret Code Trigger System',
    'hidden feature set',
    'highly-concealed',
    'Silent Shutter Strategy',
    'background events without disrupting',
]:
    if forbidden.lower() in readme.lower():
        raise SystemExit(
            f'Nova commercial docs guard: README contains stale self-use wording: {forbidden}'
        )

required_readme_markers = [
    'Nova Calculator AI',
    'Underwater Camera',
    'AutoTap',
    'canRetrieveWindowContent=false',
    'Calculator++',
    'LICENSE',
]
for marker in required_readme_markers:
    if marker not in readme:
        raise SystemExit(f'Nova commercial docs guard: README missing required marker: {marker}')

license_text = Path('LICENSE').read_text(encoding='utf-8')
if 'Apache License' not in license_text or 'Version 2.0, January 2004' not in license_text:
    raise SystemExit('Nova commercial docs guard: root LICENSE is not the Apache License 2.0 text')

notice = Path('NOTICE').read_text(encoding='utf-8')
for marker in ['Calculator++', 'Sergey Solovyev', 'Apache License 2.0']:
    if marker not in notice:
        raise SystemExit(f'Nova commercial docs guard: NOTICE missing attribution marker: {marker}')

privacy = Path('docs/PRIVACY_POLICY_DRAFT.md').read_text(encoding='utf-8')
if 'DRAFT' not in privacy or 'Required before publication' not in privacy:
    raise SystemExit('Nova commercial docs guard: privacy draft must remain explicitly non-production until finalized')

print('Nova commercial README, licensing and policy-document baseline OK')
PY
