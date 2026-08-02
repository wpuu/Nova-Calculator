#!/usr/bin/env python3
"""Plan-2 verification (2nd attempt, device awake & unlocked).

Path: Calculator (foreground) -> 更多选项 (overflow) -> 设置 -> 连点辅助 -> toggle switch ON.
SYSTEM_ALERT_WINDOW is already deny on the real package. Build uses
TYPE_ACCESSIBILITY_OVERLAY (API>=28). Circles must appear without overlay perm.

Verification: count org.solovyev.android.calculator windows in `dumpsys window`.
Before enabling: 1 window (CalculatorActivity). After enabling: 3 windows
(1 activity + 2 reticle overlays). Also grep for type=2009 (ACCESSIBILITY_OVERLAY).
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

def find_center(xml_text, attr="text", value=None, content_desc=False):
    pat = (rf'content-desc="{re.escape(value)}"' if content_desc
           else rf'{attr}="{re.escape(value)}"')
    m = re.search(pat, xml_text)
    if not m:
        return None
    i = m.start()
    start = xml_text.rfind("<node", max(0, i - 800), i)
    if start < 0:
        start = max(0, i - 500)
    seg = xml_text[start:start+700]
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', seg)
    if b:
        x1,y1,x2,y2 = map(int, b.groups())
        return ((x1+x2)//2, (y1+y2)//2)
    return None

def tap(cx, cy):
    print(f"  TAP ({cx},{cy})", flush=True)
    adb(f"input tap {cx} {cy}")
    time.sleep(1.5)

def read_switch(xml_text):
    m = re.search(
        r'class="[^"]*Switch[^"]*"[^>]*?checked="(true|false)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        xml_text)
    if not m:
        m = re.search(
        r'class="[^"]*Switch[^"]*"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*?checked="(true|false)"',
        xml_text)
        if not m:
            return None, None
        x1,y1,x2,y2 = map(int, m.groups()[:4])
        return m.group(5)=="true", ((x1+x2)//2,(y1+y2)//2)
    checked = m.group(1)=="true"
    x1,y1,x2,y2 = map(int, m.groups()[1:5])
    return checked, ((x1+x2)//2,(y1+y2)//2)

def win_count():
    win = adb("dumpsys window windows")
    lines = [l for l in win.splitlines() if PKG in l and "Window{" in l]
    typ2009 = win.count("type=2009")
    return len(lines), typ2009, lines

# ---- STEP 1: 更多选项 ----
print("=== STEP1: 更多选项 (overflow) ===", flush=True)
xml = dump_ui("p2_overflow")
c = find_center(xml, content_desc=True, value="\u66f4\u591a\u9009\u9879")  # 更多选项
print(f"  overflow center={c}", flush=True)
if not c:
    print("  [FAIL] overflow not found", flush=True); sys.exit(1)
tap(*c)

# ---- STEP 2: 设置 ----
print("=== STEP2: 设置 ===", flush=True)
time.sleep(1)
xml = dump_ui("p2_settings")
c = find_center(xml, attr="text", value="\u8bbe\u7f6e")  # 设置
print(f"  设置 center={c}", flush=True)
if not c:
    print("  [WARN] 设置 not found; texts:", [t for t in re.findall(r'text=\"([^\"]+)\"',xml) if t][:15], flush=True)
    sys.exit(1)
tap(*c)

# ---- STEP 3: 连点辅助 ----
print("=== STEP3: 连点辅助 ===", flush=True)
time.sleep(1.5)
xml = dump_ui("p2_ac")
c = find_center(xml, attr="text", value="\u8fde\u70b9\u8f85\u52a9")  # 连点辅助
print(f"  连点辅助 center={c}", flush=True)
if not c:
    print("  [WARN] 连点辅助 not found; texts:", [t for t in re.findall(r'text=\"([^\"]+)\"',xml) if t][:25], flush=True)
    sys.exit(1)
tap(*c)

# ---- STEP 4: read + toggle switch ----
print("=== STEP4: switch state ===", flush=True)
time.sleep(2)
xml = dump_ui("p2_switch")
texts = [t for t in re.findall(r'text="([^"]+)"', xml) if t]
print(f"  screen texts: {texts[:25]}", flush=True)
checked, csw = read_switch(xml)
print(f"  switch checked={checked} center={csw}", flush=True)
if checked is False:
    print("  -> OFF, toggle ON", flush=True)
    tap(*csw)
    time.sleep(2)
    xml2 = dump_ui("p2_switch2")
    checked2, _ = read_switch(xml2)
    print(f"  after toggle checked={checked2}", flush=True)
elif checked is None:
    print("  [FAIL] no Switch widget", flush=True); sys.exit(1)
else:
    print("  -> already ON", flush=True)

# ---- STEP 5: wait for reconcile ----
print("=== STEP5: wait 8s for service reconcile ===", flush=True)
time.sleep(8)

# ---- STEP 6: verify ----
n, t2009, lines = win_count()
print("=== RESULT ===", flush=True)
print(f"  windows for {PKG}: {n} (expect 3: 1 activity + 2 reticles)", flush=True)
print(f"  windows type=2009 (ACCESSIBILITY_OVERLAY): {t2009}", flush=True)
for l in lines:
    print("   " + l.strip(), flush=True)

# ---- screenshot (for user to view) ----
adb("screencap -p /sdcard/scr_plan2b.png")
adb(f"pull /sdcard/scr_plan2b.png {OUT}/scr_plan2b.png")
print(f"  screenshot saved {OUT}/scr_plan2b.png", flush=True)
print("DONE", flush=True)
