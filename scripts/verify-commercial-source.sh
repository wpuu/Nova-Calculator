#!/usr/bin/env bash
set -euo pipefail

ROOT="app/src/main"

if [[ ! -d "$ROOT" ]]; then
  echo "Commercial source root not found: $ROOT" >&2
  exit 1
fi

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
)

failed=0
for pattern in "${forbidden[@]}"; do
  if grep -RInF --exclude='*.map' -- "$pattern" "$ROOT"; then
    echo "Forbidden commercial-source marker found: $pattern" >&2
    failed=1
  fi
done

# Catch common bearer-style secrets without blocking generic API client code.
if grep -RInE -- 'sk-[A-Za-z0-9_-]{16,}' "$ROOT"; then
  echo "Possible embedded API secret found in commercial Android source" >&2
  failed=1
fi

if [[ "$failed" -ne 0 ]]; then
  echo "Commercial source guard failed." >&2
  exit 1
fi

echo "Commercial source guard passed."
