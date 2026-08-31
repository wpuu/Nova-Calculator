#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path
import re

path = Path('app/build.gradle')
text = path.read_text(encoding='utf-8')

expected_prod = 'com.wpuu.novacalculator'
expected_debug_suffix = '.dev'

literal = re.search(r'^\s*applicationId\s+["\']([^"\']+)["\']\s*$', text, re.MULTILINE)
variable = re.search(r'^\s*applicationId\s+([A-Za-z_][A-Za-z0-9_]*)\s*$', text, re.MULTILINE)
constant = re.search(
    r'^\s*def\s+novaProductionApplicationId\s*=\s*["\']([^"\']+)["\']\s*$',
    text,
    re.MULTILINE,
)
debug = re.search(r'^\s*applicationIdSuffix\s+["\']([^"\']+)["\']\s*$', text, re.MULTILINE)

if literal:
    prod = literal.group(1)
elif variable and variable.group(1) == 'novaProductionApplicationId' and constant:
    prod = constant.group(1)
else:
    raise SystemExit('Nova identity guard: applicationId is missing or not bound to the frozen production identity')

if prod != expected_prod:
    raise SystemExit(f'Nova identity guard: release applicationId must be {expected_prod}, got {prod}')
if not debug:
    raise SystemExit('Nova identity guard: debug applicationIdSuffix is missing')
if debug.group(1) != expected_debug_suffix:
    raise SystemExit(
        f'Nova identity guard: debug applicationIdSuffix must be {expected_debug_suffix}, got {debug.group(1)}'
    )

resolved_debug = prod + debug.group(1)
if resolved_debug != 'com.wpuu.novacalculator.dev':
    raise SystemExit(f'Nova identity guard: unexpected debug application id {resolved_debug}')
if prod.endswith('.dev'):
    raise SystemExit('Nova identity guard: release applicationId must never end with .dev')

print(f'Nova Android identities OK: release={prod} debug={resolved_debug}')
PY
