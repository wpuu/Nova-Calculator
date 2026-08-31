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
    Path('docs/COMMERCIAL_IDENTITY_AUDIT_V1.md'),
    Path('docs/COMMERCIAL_RELEASE_READINESS_CURRENT.md'),
    Path('app/src/main/assets/legal/LICENSE.txt'),
    Path('app/src/main/assets/legal/NOTICE.txt'),
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

license_path = Path('LICENSE')
notice_path = Path('NOTICE')
license_text = license_path.read_text(encoding='utf-8')
if 'Apache License' not in license_text or 'Version 2.0, January 2004' not in license_text:
    raise SystemExit('Nova commercial docs guard: root LICENSE is not the Apache License 2.0 text')

notice = notice_path.read_text(encoding='utf-8')
for marker in ['Calculator++', 'Sergey Solovyev', 'Apache License 2.0']:
    if marker not in notice:
        raise SystemExit(f'Nova commercial docs guard: NOTICE missing attribution marker: {marker}')

if Path('app/src/main/assets/legal/LICENSE.txt').read_bytes() != license_path.read_bytes():
    raise SystemExit('Nova commercial docs guard: APK LICENSE asset must exactly match root LICENSE')
if Path('app/src/main/assets/legal/NOTICE.txt').read_bytes() != notice_path.read_bytes():
    raise SystemExit('Nova commercial docs guard: APK NOTICE asset must exactly match root NOTICE')

privacy = Path('docs/PRIVACY_POLICY_DRAFT.md').read_text(encoding='utf-8')
if 'DRAFT' not in privacy or 'Required before publication' not in privacy:
    raise SystemExit('Nova commercial docs guard: privacy draft must remain explicitly non-production until finalized')

historical_audit = Path('docs/COMMERCIAL_IDENTITY_AUDIT_V1.md').read_text(encoding='utf-8')
if 'SUPERSEDED historical audit' not in historical_audit or 'COMMERCIAL_RELEASE_READINESS_CURRENT.md' not in historical_audit:
    raise SystemExit('Nova commercial docs guard: V1 identity audit must remain marked as superseded and point to current readiness')

readiness = Path('docs/COMMERCIAL_RELEASE_READINESS_CURRENT.md').read_text(encoding='utf-8')
for marker in [
    'P0 external production blockers',
    'com.wpuu.novacalculator',
    'NOVA_PRIVACY_CONTACT_VERIFIED=true',
    'Google Play application and Play Integrity',
    'Google Play Billing products',
    'Google Play policy declarations',
    'Repository-side next quality work (P1)',
]:
    if marker not in readiness:
        raise SystemExit(f'Nova commercial docs guard: current release readiness missing marker: {marker}')

privacy_route = Path('gateway/api/privacy.mjs')
privacy_module = Path('gateway/src/privacy-policy-page.mjs')
if not privacy_route.is_file() or not privacy_module.is_file():
    raise SystemExit('Nova commercial docs guard: public privacy-policy route is missing')

settings = Path('app/src/main/java/org/solovyev/android/calculator/SettingsActivity.kt').read_text(encoding='utf-8')
layout = Path('app/src/main/res/layout/activity_settings.xml').read_text(encoding='utf-8')
if '/api/privacy' not in settings or 'btnPrivacyPolicy' not in layout:
    raise SystemExit('Nova commercial docs guard: in-app privacy-policy entry is missing')
if 'btnOpenSourceLicenses' not in layout or 'legal/LICENSE.txt' not in settings:
    raise SystemExit('Nova commercial docs guard: in-app open-source-license entry is missing')

print('Nova commercial README, licensing, packaged notices, privacy baseline and release-readiness docs OK')
PY
