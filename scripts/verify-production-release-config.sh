#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path
from urllib.parse import urlparse
import os
import re

build_gradle = Path('app/build.gradle').read_text(encoding='utf-8')

prod = re.search(r'^\s*applicationId\s+["\']([^"\']+)["\']\s*$', build_gradle, re.MULTILINE)
debug = re.search(r'^\s*applicationIdSuffix\s+["\']([^"\']+)["\']\s*$', build_gradle, re.MULTILINE)
if not prod or prod.group(1) != 'com.wpuu.novacalculator':
    raise SystemExit('Nova production release guard: release applicationId must be com.wpuu.novacalculator')
if not debug or debug.group(1) != '.dev':
    raise SystemExit('Nova production release guard: debug applicationIdSuffix must be .dev')

required_paths = {
    'NOVA_AI_GATEWAY_URL': '/api/ai',
    'NOVA_ANONYMOUS_SESSION_URL': '/api/session',
    'NOVA_BILLING_URL': '/api/billing',
}
origins = set()
for name, expected_path in required_paths.items():
    value = os.environ.get(name, '').strip()
    if not value:
        raise SystemExit(f'Nova production release guard: {name} is required')
    try:
        parsed = urlparse(value)
    except Exception:
        raise SystemExit(f'Nova production release guard: {name} is not a valid URL')
    if parsed.scheme != 'https' or not parsed.hostname:
        raise SystemExit(f'Nova production release guard: {name} must use HTTPS')
    if parsed.username or parsed.password or parsed.fragment or parsed.query:
        raise SystemExit(f'Nova production release guard: {name} must not contain credentials, query or fragment')
    host = parsed.hostname.lower().rstrip('.')
    if host in {'localhost', '127.0.0.1', '::1'} or host.endswith(('.local', '.invalid', '.test')):
        raise SystemExit(f'Nova production release guard: {name} must use a production-routable host')
    normalized_path = parsed.path.rstrip('/') or '/'
    if normalized_path != expected_path:
        raise SystemExit(
            f'Nova production release guard: {name} path must be {expected_path}, got {normalized_path}'
        )
    port = parsed.port
    default_port = 443 if port in (None, 443) else port
    origins.add((host, default_port))

if len(origins) != 1:
    raise SystemExit('Nova production release guard: AI, session and billing endpoints must share one HTTPS origin')

project_text = os.environ.get('NOVA_PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER', '').strip()
if not re.fullmatch(r'[1-9][0-9]{0,19}', project_text):
    raise SystemExit('Nova production release guard: NOVA_PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER must be a positive integer')
project_number = int(project_text)
if project_number > 9_223_372_036_854_775_807:
    raise SystemExit('Nova production release guard: Play Integrity Cloud project number is out of range')

version_code_text = os.environ.get('NOVA_VERSION_CODE', '').strip()
if not re.fullmatch(r'[1-9][0-9]{0,9}', version_code_text):
    raise SystemExit('Nova production release guard: NOVA_VERSION_CODE must be a positive integer')
version_code = int(version_code_text)
if version_code > 2_100_000_000:
    raise SystemExit('Nova production release guard: NOVA_VERSION_CODE exceeds Google Play limit')

version_name = os.environ.get('NOVA_VERSION_NAME', '').strip()
if not re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z._-]{0,49}', version_name):
    raise SystemExit('Nova production release guard: NOVA_VERSION_NAME is invalid')

print(
    'Nova production release config OK: '
    f'applicationId={prod.group(1)} origin=https://{next(iter(origins))[0]} '
    f'versionCode={version_code} versionName={version_name} PlayIntegrity=enabled'
)
PY
