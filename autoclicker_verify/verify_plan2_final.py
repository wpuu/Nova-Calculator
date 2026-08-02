#!/usr/bin/env python3
"""Plan-2 final verification with robust toggle + window polling.

Robustly turns the autoclicker switch ON (tap while UI says OFF, stop once ON),
then polls `dumpsys window windows` for reticle overlay windows:
  reticle window = a line "Window{... u0 org.solovyev.android.calculator}:"  (NO '/Activity')
Activity windows show the full component "org.solovyev.android.calculator/....Activity".
TYPE_ACCESSIBILITY_OVERLAY windows are NOT tagged 'type=2009' in this dump, so we
count the package-only windows instead. Expect 2 reticles (+ CalculatorActivity +
PreferencesActivity if still on that screen).
"""
import subprocess, re, time, sys

ADB = r"D:/SDK/platform-tools/adb.exe"
DEVICE = "URU0218B01000786"
OUT = r"E:/Nova Calculator/autoclicker_verify"
PKG = "org.solovyev.android.calculator"

def adb(cmd):
    full = [ADB, "-s", DEVICE] + ("shell " + cmd).split()
    try:
        r = subprocess.run(full, capture_output=True, text=True, timeout=20)
        return r.stdout.strip()
    except Exception as e:
        return f"ERROR:{e}"

def dump_ui(name):
    adb(f"uiautomator dump /sdcard/{name}.xml")
    raw = adb(f"cat /sdcard/{name}.xml")
    with open(f"{OUT}/{name}.xml", "w", encoding="utf-8", errors="ignore") as f:
        f.write(raw)
    return raw

def switch_text(raw):
    return [t for t in re.findall(r'text="([^"]+)"', raw) if t]

def tap_switch():
    # switch bounds ~ (888,355,1032,499); tap center-right of the toggle
    adb("input tap 1000 427")
    time.sleep(2)

def reticle_count():
    win = adb("dumpsys window windows")
    # package-only windows (overlay, no activity component)
    ret = len(re.findall(r'Window\{[^\n]*? u0 ' + re.escape(PKG) + r'\}:', win))
    # activity windows (have '/')
    act = len(re.findall(r'Window\{[^\n]*? u0 ' + re.escape(PKG) + r'/', win))
    return ret, act, win

# ---- ensure ON ----
print("=== Toggle autoclicker ON (robust) ===", flush=True)
on_patterns = ["已开启", "已请求开启"]
max_tries = 5
for i in range(max_tries):
    raw = dump_ui(f"p2r_{i}")
    txt = switch_text(raw)
    joined = " ".join(txt)
    print(f"  try {i}: {[t for t in txt if '开启' in t or '请求' in t or '授予' in t]}", flush=True)
    if any(p in joined for p in on_patterns):
        print("  -> already ON, stop toggling", flush=True)
        break
    tap_switch()
else:
    print("  [WARN] could not turn ON after tries", flush=True)

# ---- poll for reticles ----
print("=== Poll reticle windows (12s) ===", flush=True)
best = 0
best_win = ""
for s in range(8):
    ret, act, win = reticle_count()
    print(f"  t={s*1.5:.1f}s reticle_windows={ret} activity_windows={act}", flush=True)
    if ret > best:
        best = ret; best_win = win
    if ret >= 2:
        print("  -> 2 reticles detected!", flush=True)
        break
    time.sleep(1.5)

print(f"\n=== RESULT ===", flush=True)
print(f"  MAX reticle overlay windows seen: {best}", flush=True)
print(f"  (expect 2 = red + blue circles; activity windows also present)", flush=True)
# show the reticle window lines
for ln in best_win.splitlines():
    if re.search(r'Window\{[^\n]*? u0 ' + re.escape(PKG) + r'\}:', ln):
        print("   RETICLE:", ln.strip(), flush=True)

# ---- screenshot for the record ----
adb("screencap -p /sdcard/scr_plan2_final.png")
adb(f"pull /sdcard/scr_plan2_final.png {OUT}/scr_plan2_final.png")
print(f"  screenshot: {OUT}/scr_plan2_final.png", flush=True)
print("DONE", flush=True)
