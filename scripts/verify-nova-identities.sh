#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path
import re

path = Path('app/build.gradle')
text = path.read_text(encoding='utf-8')

prod = re.search(r'^\s*applicationId\s+["\']([^"\']+)["\']\s*$', text, re.MULTILINE)
debug = re.search(r'^\s*applicationIdSuffix\s+["\']([^"\']+)["\']\s*$', text, re.MULTILINE)

expected_prod = 'com.wpuu.novacalculator'
expected_debug_suffix = '.dev'

if not prod:
    raise SystemExit('Nova identity guard: applicationId is missing')
if prod.group(1) != expected_prod:
    raise SystemExit(f'Nova identity guard: release applicationId must be {expected_prod}, got {prod.group(1)}')
if not debug:
    raise SystemExit('Nova identity guard: debug applicationIdSuffix is missing')
if debug.group(1) != expected_debug_suffix:
    raise SystemExit(
        f'Nova identity guard: debug applicationIdSuffix must be {expected_debug_suffix}, got {debug.group(1)}'
    )

resolved_debug = prod.group(1) + debug.group(1)
if resolved_debug != 'com.wpuu.novacalculator.dev':
    raise SystemExit(f'Nova identity guard: unexpected debug application id {resolved_debug}')
if prod.group(1).endswith('.dev'):
    raise SystemExit('Nova identity guard: release applicationId must never end with .dev')

print(f'Nova Android identities OK: release={prod.group(1)} debug={resolved_debug}')
PY
