#!/usr/bin/env bash
set -euo pipefail

# Production release gate only. NOVA_PRIVACY_CONTACT_VERIFIED is an operator sign-off that must be
# set to the exact string "true" only after the production publisher has actually verified control
# of the public privacy-contact domain/mailbox. It is deliberately not treated as cryptographic
# proof of domain or mailbox ownership.
if [ "${NOVA_PRIVACY_CONTACT_VERIFIED:-}" != "true" ]; then
  echo 'NOVA_PRIVACY_CONTACT_VERIFIED must be true only after the production privacy domain/mailbox has been verified.' >&2
  exit 1
fi

POLICY_FILE="${1:-}"
if [ -z "$POLICY_FILE" ] || [ ! -s "$POLICY_FILE" ]; then
  echo 'Production privacy readiness requires a non-empty fetched privacy-policy file.' >&2
  exit 1
fi

python3 - "$POLICY_FILE" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding='utf-8', errors='replace')
lower = text.lower()

blocked_markers = [
    'release draft',
    'before public release',
    'verify the domain',
    'tbd',
    'placeholder',
    'todo',
]
found = [marker for marker in blocked_markers if marker in lower]
if found:
    raise SystemExit(
        'Production privacy policy still contains unresolved release markers: '
        + ', '.join(found)
    )

required_markers = [
    'Nova Calculator AI Privacy Policy',
    'Privacy contact:',
]
for marker in required_markers:
    if marker not in text:
        raise SystemExit(f'Production privacy policy is missing required marker: {marker}')

print('Nova production privacy readiness gate passed.')
PY
