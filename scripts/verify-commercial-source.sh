#!/usr/bin/env bash
set -euo pipefail

SCAN_PATHS=(
  "app/src/main"
  "app/build.gradle"
)

for path in "${SCAN_PATHS[@]}"; do
  if [[ ! -e "$path" ]]; then
    echo "Commercial source path not found: $path" >&2
    exit 1
  fi
done

forbidden=(
  'SecretCodeEvent'
  'secretCodePhoto'
  'secretCodeVideoStart'
  'secretCodeVideoStop'
  'secretCodeAudioStart'
  'secretCodeAudioStop'
  'hiddenPreview'
  'startHiddenVideo'
  'startHiddenAudio'
  'takeHiddenPhoto'
  'VideoRecorderManager'
  'AudioRecorderManager'
  'apihub.agnes-ai.com'
  'agnes-2.5-flash'
  'AGNES_API_KEY'
  'AGNES_API_KEYS'
  'ca-app-pub-'
  'com.google.android.gms:play-services-ads'
  'com.google.android.gms.ads.'
  'com.google.firebase:firebase-analytics'
  'com.google.firebase.analytics.'
  'https://example.com/nova-calculator'
  'facebook.com/calculatorpp'
  'play.google.com/store/apps/details?id=org.solovyev.android.calculator'
  'market://details?id=org.solovyev.android.calculator'
)

failed=0
for pattern in "${forbidden[@]}"; do
  if grep -RInF --exclude='*.map' -- "$pattern" "${SCAN_PATHS[@]}"; then
    echo "Forbidden commercial-source marker found: $pattern" >&2
    failed=1
  fi
done

# Catch common bearer-style secrets without blocking generic API client code.
if grep -RInE -- 'sk-[A-Za-z0-9_-]{16,}' "${SCAN_PATHS[@]}"; then
  echo "Possible embedded API secret found in commercial Android source" >&2
  failed=1
fi

if [[ "$failed" -ne 0 ]]; then
  echo "Commercial source guard failed." >&2
  exit 1
fi

echo "Commercial source guard passed."
