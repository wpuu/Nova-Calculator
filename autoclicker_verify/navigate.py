#!/usr/bin/env python3
"""AutoClicker visual verification navigation script.
Dumps UI -> parses coords -> taps -> repeats until circles are showing.
Final step: grants accessibility via settings put + screencap.
"""
import subprocess, re, time, sys

ADB = r"D:/SDK/platform-tools/adb.exe"
DEVICE = "URU0218B01000786"
OUT = r"E:/Nova Calculator/autoclicker_verify"

def adb(cmd):
    full = [ADB, "-s", DEVICE] + ("shell " + cmd).split()
    try:
        r = subprocess.run(full, capture_output=True, text=True, timeout=15)
        return r.stdout.strip()
    except Exception as e:
        return f"ERROR:{e}"

def dump_ui(name="ui"):
    adb(f"uiautomator dump /sdcard/{name}.xml")
    raw = adb(f"cat /sdcard/{name}.xml")
    path = f"{OUT}/{name}.xml"
    with open(path, "w", encoding="utf-8") as f:
        f.write(raw)
    return raw

def find_center(xml_text, attr="text", value=None, content_desc=False):
    """Find first node matching attr=value, return (cx, cy) or None."""
    if content_desc:
        pattern = rf'content-desc="{re.escape(value)}"'
    else:
        pattern = rf'{attr}="{re.escape(value)}"'
    m = re.search(pattern, xml_text)
    if not m:
        return None
    # Search backwards for opening <node tag to get bounds
    i = m.start()
    start = xml_text.rfind("<node", max(0, i - 800), i)
    if start < 0:
        start = max(0, i - 500)
    seg = xml_text[start:start+600]
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', seg)
    if b:
        x1,y1,x2,y2 = map(int, b.groups())
        return ((x1+x2)//2, (y1+y2)//2)
    return None

def tap(cx, cy):
    print(f"  TAP ({cx},{cy})", flush=True)
    adb(f"input tap {cx} {cy}")
    time.sleep(1.5)

def screencap(name="scr"):
    adb(f"screencap -p /sdcard/{name}.png")
    adb(f"pull /sdcard/{name}.png {OUT}/{name}.png")
    return f"{OUT}/{name}.png"

# ---- Step 1: Skip wizard ----
print("=== STEP 1: Skip wizard ===", flush=True)
xml = dump_ui("nav_wizard")
c = find_center(xml, attr="text", value="\u8df3\u8fc7")  # 跳过
if c:
    tap(*c)
else:
    print("  [WARN] No skip found", flush=True)

# Step 1b: Confirm wizard exit (是)
time.sleep(1.5)
xml = dump_ui("nav_confirm")
c_yes = find_center(xml, attr="text", value="\u662f")  # 是
if c_yes:
    tap(*c_yes)
else:
    print("  (No confirm dialog)", flush=True)

# ---- Step 2: Open overflow menu ===
print("=== STEP 2: Open menu ===", flush=True)
time.sleep(1)
xml = dump_ui("nav_calc")
c_menu = find_center(xml, content_desc=True, value="\u66f4\u591a\u9009\u9879")  # 更多选项
if c_menu:
    tap(*c_menu)
else:
    print("  [WARN] Menu not found", flush=True)

# ---- Step 3: Tap Settings ----
print("=== STEP 3: Tap Settings ===", flush=True)
time.sleep(1.5)
xml = dump_ui("nav_menu")
c_set = find_center(xml, attr="text", value="\u8bbe\u7f6e")  # 设置
if c_set:
    tap(*c_set)
else:
    print("  [WARN] Settings not found", flush=True)

# ---- Step 4: Enter AutoClicker ----
print("=== STEP 4: Enter AutoClicker ===", flush=True)
time.sleep(1.5)
xml = dump_ui("nav_prefs")
c_ac = find_center(xml, attr="text", value="\u8fde\u70b9\u8f85\u52a9")  # 连点辅助
if c_ac:
    tap(*c_ac)
else:
    print("  [WARN] AutoClicker not found", flush=True)

# ---- Step 5: Toggle switch ON (sets intent=true, jumps to system settings) ----
print("=== STEP 5: Toggle autoclicker switch ON ===", flush=True)
time.sleep(2)
xml = dump_ui("nav_ac_screen")
texts = re.findall(r'text="([^"]*)"', xml)
nonempty = [t for t in texts if t]
print(f"  Screen texts: {nonempty[:15]}", flush=True)

# The switch row is usually a large area; tap the center of it
# Find any Switch widget or the main container
switch_bounds = re.search(r'class="android.widget.Switch"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if switch_bounds:
    x1,y1,x2,y2 = map(int, switch_bounds.groups())
    c_switch = ((x1+x2)//2, (y1+y2)//2)
else:
    # Fallback: look for the whole autoclicker preference area
    c_switch = find_center(xml, attr="text", value="\u8fde\u70b9\u8f85\u52a9")

if c_switch:
    tap(*c_switch)
else:
    print("  [WARN] Switch not found!", flush=True)

# ---- Step 6: Grant accessibility & wait for overlay ----
print("=== STEP 6: Grant accessibility ===", flush=True)
time.sleep(2)
COMP = "org.solovyev.android.calculator/org.solovyev.android.calculator.autoclicker.AutoClickerService"
adb(f'settings put secure enabled_accessibility_services {COMP}')
time.sleep(4)  # Wait for service bind + reconcileState

# Verify overlay appeared
result = adb("dumpsys window windows")
count = result.count('package=org.solovyev.android.calculator appop=SYSTEM_ALERT_WINDOW')
grant = adb("settings get secure enabled_accessibility_services")
print(f"\n=== RESULT ===", flush=True)
print(f"  Accessibility grant: {grant}", flush=True)
print(f"  Overlay windows: {count} (expect 2)", flush=True)

# Screencap!
path = screencap("scr_hollow_circles")
print(f"\n  Screenshot saved: {path}", flush=True)
