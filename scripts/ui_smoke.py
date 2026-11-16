#!/usr/bin/env python3
"""Drives Gideon on an emulator: visits every screen, takes screenshots, reports crashes."""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "app.gideon.news"
ACTIVITY = f"{PKG}/app.gideon.MainActivity"
APK = "app/build/outputs/apk/release/app-release.apk"
OUT = "ui-shots"
os.makedirs(OUT, exist_ok=True)
problems = []


def adb(*args, binary=False, timeout=60):
    r = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def shot(name):
    png = adb("exec-out", "screencap", "-p", binary=True)
    with open(f"{OUT}/{name}.png", "wb") as f:
        f.write(png)
    print(f"[shot] {name}", flush=True)


def alive():
    return adb("shell", "pidof", PKG).strip() != ""


def check(step):
    crash = adb("logcat", "-b", "crash", "-d")
    if PKG in crash or "FATAL EXCEPTION" in crash:
        problems.append(f"CRASH after '{step}'")
        with open(f"{OUT}/crash.txt", "a") as f:
            f.write(f"===== after {step} =====\n{crash}\n")
        print(f"!!! CRASH after {step}\n{crash[-4000:]}", flush=True)
        adb("logcat", "-b", "crash", "-c")
        relaunch()
    elif not alive():
        problems.append(f"APP CLOSED after '{step}'")
        print(f"!!! app not running after {step}", flush=True)
        relaunch()


def relaunch():
    adb("shell", "am", "start", "-n", ACTIVITY)
    time.sleep(8)


def nodes():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", timeout=40)
        xml = adb("shell", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in xml:
            try:
                return list(ET.fromstring(xml[xml.index("<hierarchy"):]).iter("node"))
            except ET.ParseError:
                pass
        time.sleep(1)
    return []


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "[0,0][0,0]")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(label, prefix=False):
    for n in nodes():
        for attr in ("text", "content-desc"):
            v = (n.get(attr) or "").strip()
            if v and (v == label or (prefix and v.startswith(label))):
                return center(n)
    return None


def tap_label(label, prefix=False, wait=20):
    end = time.time() + wait
    while time.time() < end:
        pos = find(label, prefix)
        if pos:
            adb("shell", "input", "tap", str(pos[0]), str(pos[1]))
            print(f"[tap] {label} at {pos}", flush=True)
            return True
        time.sleep(2)
    problems.append(f"not found: {label}")
    print(f"??? not found: {label}", flush=True)
    return False


W, H = 1080, 2400
size = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
if size:
    W, H = int(size.group(1)), int(size.group(2))


def swipe_up(ms=260):
    adb("shell", "input", "swipe", str(W // 2), str(int(H * 0.75)), str(W // 2), str(int(H * 0.22)), str(ms))


def tap_center():
    adb("shell", "input", "tap", str(W // 2), str(int(H * 0.42)))


def back():
    adb("shell", "input", "keyevent", "4")


# ---------------------------------------------------------------- run
adb("install", "-r", "-g", APK, timeout=240)
adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
adb("shell", "cmd", "uimode", "night", "yes")
adb("logcat", "-c")
adb("logcat", "-b", "crash", "-c")
relaunch()

# Feed
end = time.time() + 90
while time.time() < end and not find("J'aime"):
    time.sleep(3)
time.sleep(4)
shot("01-feed")
check("launch")
swipe_up(); time.sleep(5); shot("02-feed-next"); check("swipe 1")
swipe_up(); time.sleep(5); shot("03-feed-next2"); check("swipe 2")

# Videos
if tap_label("Vidéos"):
    time.sleep(12); shot("04-videos"); check("videos tab")
    time.sleep(6); shot("05-videos-later")
    tap_center(); time.sleep(2); shot("06-video-paused"); check("pause")
    tap_center(); time.sleep(3)
    swipe_up(); time.sleep(8); shot("07-video-next"); check("next video")
    tap_label("Pour toi"); time.sleep(4)

# Reader
if tap_label("Lire l'article", prefix=True):
    time.sleep(7); shot("08-reader"); check("open reader")
    swipe_up(500); time.sleep(2); shot("09-reader-scrolled")
    back(); time.sleep(2); shot("10-after-reader"); check("close reader")

# Search: keyboard, category, back
if tap_label("Rechercher"):
    time.sleep(3); shot("11-search-keyboard"); check("open search")
    back(); time.sleep(1); shot("12-search"); check("hide keyboard")
    if tap_label("À la une"):
        time.sleep(4); shot("13-une-from-search"); check("À la une from search")
    back(); time.sleep(2)
if tap_label("Rechercher"):
    time.sleep(2); back(); time.sleep(1); back(); time.sleep(3)
    shot("14-back-from-search"); check("back from search")

# Saved & Settings
if tap_label("Gardés"):
    time.sleep(3); shot("15-saved"); check("saved")
if tap_label("Réglages"):
    time.sleep(3); shot("16-settings"); check("settings")
    swipe_up(); time.sleep(2); shot("17-settings-scrolled")
adb("shell", "cmd", "uimode", "night", "no"); time.sleep(4)
shot("18-settings-light"); check("light mode")
tap_label("Retour"); time.sleep(2)  # folded tab bar → unfold
tap_label("À la une"); time.sleep(4); shot("19-feed-light"); check("feed light")

app_log = adb("logcat", "-d", "-v", "brief", "Gideon:V", "*:S")
with open(f"{OUT}/app-log.txt", "w") as f:
    f.write(app_log)
print("---- app log ----\n" + app_log[-3000:])

errors = adb("logcat", "-d", "-v", "brief", "*:E")
with open(f"{OUT}/errors.txt", "w") as f:
    f.write("\n".join(l for l in errors.splitlines() if "gideon" in l.lower() or "AndroidRuntime" in l))

print("\n==== UI SMOKE SUMMARY ====")
print("\n".join(problems) if problems else "OK: no crash, every screen reached")
sys.exit(1 if any(p.startswith(("CRASH", "APP CLOSED")) for p in problems) else 0)
