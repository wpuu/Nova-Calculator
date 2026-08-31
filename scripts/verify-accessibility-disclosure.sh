#!/usr/bin/env bash
set -euo pipefail

settings='app/src/main/java/org/solovyev/android/calculator/SettingsActivity.kt'
service='app/src/main/res/xml/accessibility_service_config.xml'

for path in "$settings" "$service"; do
  [[ -f "$path" ]] || { echo "Accessibility guard: missing $path" >&2; exit 1; }
done

require_fixed() {
  local pattern="$1"
  local path="$2"
  if ! grep -Fq -- "$pattern" "$path"; then
    echo "Accessibility guard: required marker missing from $path: $pattern" >&2
    exit 1
  fi
}

require_fixed 'PREF_AUTOTAP_ACCESSIBILITY_DISCLOSURE_V1' "$settings"
require_fixed 'setPositiveButton("同意并继续")' "$settings"
require_fixed 'setNegativeButton("不同意")' "$settings"
require_fixed 'requestAutoClickerEnable(switchEnabled)' "$settings"
require_fixed 'android:canRetrieveWindowContent="false"' "$service"
require_fixed 'android:accessibilityEventTypes="typeWindowStateChanged|typeWindowsChanged"' "$service"
require_fixed 'android:accessibilityFeedbackType="feedbackGeneric"' "$service"
require_fixed 'android:canPerformGestures="true"' "$service"
require_fixed 'android:canRequestFilterKeyEvents="true"' "$service"

if grep -Fq 'android:isAccessibilityTool="true"' "$service"; then
  echo 'Accessibility guard: Nova AutoTap must not claim to be a disability accessibility tool' >&2
  exit 1
fi
if grep -Fq 'android:canRetrieveWindowContent="true"' "$service"; then
  echo 'Accessibility guard: window-content retrieval must remain disabled' >&2
  exit 1
fi

echo 'Nova AccessibilityService disclosure/scope guard passed.'
