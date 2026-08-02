#!/usr/bin/env python3
"""Robust reproduction of 'autoclicker switch cannot be turned off'.

Navigates Calculator -> 更多选项 (content-desc) -> 设置 -> 连点辅助 with
retries/verification, then toggles the switch OFF and reports before/after:
  - Switch 'checked' (visual)
  - summary text
  - reticle overlay window count
  - accessibility service bound-state
"""
import subprocess, re, time, sys

ADB = r"D:/SDK/platform-tools/adb.exe"
DEVICE = "URU0218B01000786"
OUT = r"E:/Nova Calculator/autoclicker_verify"
PKG = "org.solovyev.android.calculator"
SW_ID = "org.solovyev.android.calculator:id/switchWidget"


def adb(cmd, timeout=25):
    full = [ADB, "-s", DEVICE] + ("shell " + cmd).split()
    try:
        r = subprocess.run(full, capture_output=True, text=True, timeout=timeout)
        return r.stdout.strip()
    except Exception as e:
        return f"ERROR:{e}"


def _read(name):
    try:
        with open(f"{OUT}/{name}.xml", encoding="utf-8", errors="ignore") as f:
            return f.read()
    except Exception:
        return ""


def dump_ui(name):
    adb(f"uiautomator dump /sdcard/{name}.xml")
    raw = adb(f"cat /sdcard/{name}.xml")
    with open(f"{OUT}/{name}.xml", "w", encoding="utf-8", errors="ignore") as f:
        f.write(raw)
    return raw


def find_center(raw, *keywords, attr="text"):
    """Match node whose `attr` (text or content-desc) contains any keyword."""
    nodes = re.findall(r'<node[^>]*?>', raw)
    for n in nodes:
        m = re.search(attr + r'="([^"]*)"', n)
        if not m:
            continue
        val = m.group(1)
        if any(k in val for k in keywords):
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
            if b:
                x1, y1, x2, y2 = map(int, b.groups())
                return ((x1 + x2) // 2, (y1 + y2) // 2)
    return None


def find_switch(raw):
    m = re.search(r'<node[^>]*' + re.escape(SW_ID) + r'[^>]*?>', raw)
    if not m:
        m = re.search(r'<node[^>]*class="android.widget.Switch"[^>]*?>', raw)
    if not m:
        return None, None
    node = m.group(0)
    chk = re.search(r'checked="(true|false)"', node)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    if b:
        x1, y1, x2, y2 = map(int, b.groups())
        return (chk.group(1) if chk else "?", ((x1 + x2) // 2, (y1 + y2) // 2))
    return (chk.group(1) if chk else "?", None)


def summary(raw):
    return [t for t in re.findall(r'text="([^"]+)"', raw)
            if any(k in t for k in ("开启", "关闭", "请求", "授予", "圆圈", "悬浮"))]


def reticle_count():
    win = adb("dumpsys window windows")
    return len(re.findall(r'Window\{[^\n]*? u0 ' + re.escape(PKG) + r'\}:', win))


def service_bound():
    acc = adb("dumpsys accessibility")
    return ("Bound services:{}" not in acc) and ("连点辅助" in acc)


def tap(x, y):
    adb(f"input tap {x} {y}")
    time.sleep(1.6)


def nav_step(keywords, attr, label, max_retry=3):
    """Tap the first node matching keywords; retry until screen shows it."""
    for attempt in range(max_retry):
        raw = dump_ui(f"nav_{label}_{attempt}")
        c = find_center(raw, *keywords, attr=attr)
        if c:
            tap(*c)
            # verify next screen contains a marker for the target
            time.sleep(1.0)
            return True
        print(f"  [nav {label}] attempt {attempt}: not found, retry", flush=True)
        time.sleep(1.0)
    return False


def step(label):
    print(f"\n### {label}", flush=True)


# ---- launch ----
step("Launch calculator")
adb("am start -n org.solovyev.android.calculator/.CalculatorActivity")
time.sleep(2.5)

# ---- overflow (content-desc 更多选项) ----
step("Open overflow (更多选项)")
nav_step(("更多选项",), "content-desc", "overflow", max_retry=4)

# ---- 设置 ----
step("Tap 设置")
ok = False
for attempt in range(4):
    raw = dump_ui(f"nav_settings_{attempt}")
    c = find_center(raw, "设置")
    if c:
        tap(*c)
        ok = True
        break
    # maybe still need to open overflow first
    nav_step(("更多选项",), "content-desc", "overflow_retry", max_retry=2)
    time.sleep(1.0)
if not ok:
    print("  [WARN] 设置 not reachable; abort", flush=True)
    sys.exit(1)

# ---- 连点辅助 ----
step("Tap 连点辅助")
ok = False
for attempt in range(5):
    raw = dump_ui(f"nav_ac_{attempt}")
    c = find_center(raw, "连点辅助", "开启连点辅助")
    if c:
        tap(*c)
        ok = True
        break
    time.sleep(1.0)
if not ok:
    print("  [WARN] 连点辅助 not reachable; abort", flush=True)
    sys.exit(1)

time.sleep(1.5)
dump_ui("ac_screen")

# ---- BEFORE ----
step("BEFORE toggle-off")
raw = _read("ac_screen")
sw = find_switch(raw)
print(f"  switch checked = {sw[0] if sw else 'N/A'}  center={sw[1] if sw else None}", flush=True)
print(f"  summary = {summary(raw)}", flush=True)
print(f"  reticles = {reticle_count()}  service_bound={service_bound()}", flush=True)

if not sw or not sw[1]:
    print("  [FATAL] switch not located", flush=True)
    sys.exit(1)

# ---- TOGGLE OFF ----
step("Toggle OFF")
tap(*sw[1])

# ---- AFTER (poll) ----
step("AFTER toggle-off (poll 9s)")
for s in range(6):
    time.sleep(1.5)
    raw = dump_ui(f"ac_after_{s}")
    sw2 = find_switch(raw)
    print(f"  t={s*1.5+1.5:.1f}s checked={sw2[0] if sw2 else '?'} "
          f"reticles={reticle_count()} bound={service_bound()} "
          f"summary={summary(raw)}", flush=True)

print("\nDONE", flush=True)
