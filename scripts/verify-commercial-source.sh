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

# AutoTap commercial invariants. These are product/safety rules, not implementation suggestions:
# - a tap must not silently regress into a tiny swipe;
# - all visible AutoTap overlays must pass touches through while a run is active;
# - the service must consume the WindowMetrics/display-monitor compatibility layer rather than
#   relying only on the deprecated default-display path.
AUTOTAP_SERVICE="app/src/main/java/org/solovyev/android/calculator/autoclicker/AutoClickerService.java"
if [[ ! -f "$AUTOTAP_SERVICE" ]]; then
  echo "AutoTap service source missing" >&2
  failed=1
else
  if grep -nF -- 'path.lineTo' "$AUTOTAP_SERVICE"; then
    echo "AutoTap stationary-tap invariant failed: swipe path found in service" >&2
    failed=1
  fi

  required_autotap_markers=(
    'AutoClickerGestureFactory.stationaryTap'
    'AutoClickerDisplayBounds.read'
    'startDisplayMonitor()'
    'floatingParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE'
    'floatingParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE'
  )
  for pattern in "${required_autotap_markers[@]}"; do
    if ! grep -qF -- "$pattern" "$AUTOTAP_SERVICE"; then
      echo "AutoTap commercial invariant missing: $pattern" >&2
      failed=1
    fi
  done
fi

if [[ "$failed" -ne 0 ]]; then
  echo "Commercial source guard failed." >&2
  exit 1
fi

echo "Commercial source guard passed."
