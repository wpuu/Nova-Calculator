#!/usr/bin/env python3
"""Plan-2 verification: circles must appear WITHOUT SYSTEM_ALERT_WINDOW permission.

The device already has SYSTEM_ALERT_WINDOW = deny on the real package
(org.solovyev.android.calculator). The build installed (11:58 today) uses
TYPE_ACCESSIBILITY_OVERLAY (API>=28), which needs only the accessibility
service to be enabled. We just have to make sure the in-app switch is ON.
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
    with open(f"{OUT}/{name}.xml", "w", encoding="utf-8") as f:
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

def screencap(name):
    adb(f"screencap -p /sdcard/{name}.png")
    adb(f"pull /sdcard/{name}.png {OUT}/{name}.png")
    return f"{OUT}/{name}.png"

def read_switch_state(xml_text):
    """Return (checked_bool, center) for the first Switch widget, or (None,None)."""
    # match: class="...Switch" ... checked="true/false" ... bounds="..."
    m = re.search(
        r'class="[^"]*Switch[^"]*"[^>]*?checked="(true|false)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        xml_text)
    if not m:
        # try reversed order: bounds before checked
        m = re.search(
        r'class="[^"]*Switch[^"]*"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*?checked="(true|false)"',
        xml_text)
        if not m:
            return None, None
        x1,y1,x2,y2 = map(int, m.groups()[:4])
        checked = m.group(5) == "true"
        return checked, ((x1+x2)//2, (y1+y2)//2)
    checked = m.group(1) == "true"
    x1,y1,x2,y2 = map(int, m.groups()[1:5])
    return checked, ((x1+x2)//2, (y1+y2)//2)

# ============ NAVIGATE ============
print("=== Open overflow menu ===", flush=True)
time.sleep(1)
xml = dump_ui("v_menu")
c = find_center(xml, content_desc=True, value="\u66f4\u591a\u9009\u9879")  # 更多选项
if c:
    tap(*c)
else:
    print("  [WARN] overflow menu not found; trying direct settings", flush=True)

print("=== Tap Settings ===", flush=True)
time.sleep(1.5)
xml = dump_ui("v_set")
c = find_center(xml, attr="text", value="\u8bbe\u7f6e")  # 设置
if c:
    tap(*c)
else:
    print("  [WARN] Settings not found", flush=True)

print("=== Enter AutoClicker ===", flush=True)
time.sleep(1.5)
xml = dump_ui("v_ac")
c = find_center(xml, attr="text", value="\u8fde\u70b9\u8f85\u52a9")  # 连点辅助
if c:
    tap(*c)
else:
    print("  [WARN] AutoClicker row not found", flush=True)

print("=== Read switch state ===", flush=True)
time.sleep(2)
xml = dump_ui("v_acscreen")
texts = [t for t in re.findall(r'text="([^"]*)"', xml) if t]
print(f"  Screen texts: {texts[:20]}", flush=True)
checked, cswitch = read_switch_state(xml)
print(f"  Switch checked={checked} center={cswitch}", flush=True)

if checked is False:
    print("  -> switch OFF, toggling ON", flush=True)
    tap(*cswitch)
    time.sleep(2)
    # re-read
    xml2 = dump_ui("v_acscreen2")
    checked2, _ = read_switch_state(xml2)
    print(f"  After toggle: checked={checked2}", flush=True)
elif checked is None:
    print("  [WARN] no Switch widget found!", flush=True)
else:
    print("  -> switch already ON, leaving as is", flush=True)

# ============ WAIT FOR CIRCLES ============
print("=== Waiting for service reconcile (8s) ===", flush=True)
time.sleep(8)

# ============ VERIFY WINDOWS ============
print("=== dumpsys window (package) ===", flush=True)
win = adb("dumpsys window windows")
pkg_windows = [ln for ln in win.splitlines() if "org.solovyev.android.calculator" in ln]
for ln in pkg_windows:
    print("  " + ln.strip(), flush=True)
# Count accessibility-overlay typed windows (type 2009 / TYPE_ACCESSIBILITY_OVERLAY)
acc = win.count("type=2009")  # TYPE_ACCESSIBILITY_OVERLAY value
appop_saw = win.count("appop=SYSTEM_ALERT_WINDOW")
print(f"  windows type=2009 (ACCESSIBILITY_OVERLAY): {acc}", flush=True)
print(f"  windows tagged appop=SYSTEM_ALERT_WINDOW: {appop_saw}", flush=True)

# ============ SCREENSHOT ============
path = screencap("scr_plan2")
print(f"  Screenshot: {path}", flush=True)
print("DONE", flush=True)
