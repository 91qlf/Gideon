"""Installs Gideon on an iPhone simulator, opens each screen and takes screenshots.

Usage: python3 sim_smoke.py <path/to/Gideon.app> <output dir>
Fails if the app is not running after a launch or if a crash report appears.
"""
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import time

APP, OUT = sys.argv[1], sys.argv[2]
BUNDLE = "app.gideon.ios"
os.makedirs(OUT, exist_ok=True)


def sh(*cmd, check=True, timeout=300):
    print("$", " ".join(cmd), flush=True)
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    for stream in (r.stdout, r.stderr):
        if stream.strip():
            print(stream.strip()[-1500:], flush=True)
    if check and r.returncode != 0:
        raise SystemExit(f"command failed ({r.returncode}): {' '.join(cmd)}")
    return r


def runtime_version(key):
    m = re.search(r"iOS-(\d+)-(\d+)(?:-(\d+))?", key)
    return tuple(int(x or 0) for x in m.groups()) if m else (0, 0, 0)


devices = json.loads(sh("xcrun", "simctl", "list", "devices", "available", "-j").stdout)["devices"]
pick = None
for runtime in sorted((k for k in devices if "iOS" in k), key=runtime_version, reverse=True):
    phones = [d for d in devices[runtime] if d["name"].startswith("iPhone")]
    pros = [d for d in phones if d["name"].endswith("Pro")]
    if phones:
        pick = (pros or phones)[0]
        print("runtime:", runtime, flush=True)
        break
if pick is None:
    raise SystemExit("no iPhone simulator available")
udid = pick["udid"]
print("device:", pick["name"], udid, flush=True)

sh("xcrun", "simctl", "boot", udid, check=False)
sh("xcrun", "simctl", "bootstatus", udid, "-b", timeout=900)
sh("xcrun", "simctl", "status_bar", udid, "override", "--time", "9:41", "--batteryState", "charged",
   "--batteryLevel", "100", "--wifiBars", "3", "--cellularBars", "4", check=False)
sh("xcrun", "simctl", "install", udid, APP)

reports = os.path.expanduser("~/Library/Logs/DiagnosticReports")
before = set(glob.glob(os.path.join(reports, "*")))

screens = [
    ("01-feed", ["-GideonTab", "feed"], 16),
    ("02-feed-videos", ["-GideonTab", "feed", "-GideonFeedTab", "videos"], 18),
    ("03-reader", ["-GideonTab", "feed", "-GideonOpenFirst", "YES"], 14),
    ("04-search", ["-GideonTab", "search"], 8),
    ("05-saved", ["-GideonTab", "saved"], 6),
    ("06-settings", ["-GideonTab", "settings"], 6),
    ("07-feed-dark-mode", ["-GideonTab", "feed"], 10),
]
problems = []
for name, args, wait in screens:
    if name == "07-feed-dark-mode":
        sh("xcrun", "simctl", "ui", udid, "appearance", "dark", check=False)
    sh("xcrun", "simctl", "terminate", udid, BUNDLE, check=False)
    sh("xcrun", "simctl", "launch", udid, BUNDLE, *args)
    time.sleep(wait)
    listing = sh("xcrun", "simctl", "spawn", udid, "launchctl", "list", check=False).stdout
    running = any(BUNDLE in line and line.split()[0] != "-" for line in listing.splitlines())
    sh("xcrun", "simctl", "io", udid, "screenshot", os.path.join(OUT, name + ".png"), check=False)
    print(f"== {name}: {'running' if running else 'NOT RUNNING'}", flush=True)
    if not running:
        problems.append(name)

crashes = [p for p in set(glob.glob(os.path.join(reports, "*"))) - before if "Gideon" in os.path.basename(p)]
for path in crashes:
    shutil.copy(path, OUT)
    print("crash report:", path)
    with open(path, errors="replace") as fh:
        print(fh.read()[:6000])

sh("xcrun", "simctl", "shutdown", udid, check=False)
if problems or crashes:
    raise SystemExit(f"app not running after: {problems}; crash reports: {len(crashes)}")
print("all screens OK")
