"""Guarded launcher/read-only snapshot for the debug human review host. Standard library only.

Never install, clear storage, change settings, sign in, or operate on an arbitrary device.
Application starts before Activity: inspect saved state BEFORE am start/broadcast.
"""
import argparse
import os
from pathlib import Path
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True, choices=["emulator-5564"])
parser.add_argument("--snapshot", action="store_true")
parser.add_argument("--adb", default=str(Path(os.environ.get("LOCALAPPDATA", "")) / "Android/Sdk/platform-tools/adb.exe"))
args = parser.parse_args()
package = "com.clarity.finance"

def adb(*parts, check=True):
    result = subprocess.run([args.adb, "-s", args.serial, *parts], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and result.returncode:
        sys.exit("STOP: device command failed; inspect privately. No launch performed.")
    return result

name = adb("emu", "avd", "name").stdout.splitlines()[0].strip()
if name != "Clarity_UI_Pilot_API26":
    sys.exit("STOP: serial does not identify the approved isolated AVD.")
listing = adb("shell", "run-as", package, "find", "shared_prefs", "files", "-type", "f").stdout
paths = [line.strip() for line in listing.splitlines() if line.strip()]
for path in paths:
    if path.startswith("shared_prefs/"):
        contents = adb("shell", "run-as", package, "cat", path).stdout
        if any(key in contents for key in ("FIREBASE_USER", "cachedTokenState", "getTokenResponse")):
            sys.exit("STOP: unexpected saved authentication. Do not launch or modify it.")
    elif not (path.startswith("files/") and "FirebaseHeartBeat" in path):
        sys.exit("STOP: unexpected saved file/recovery. Do not launch, retry, or remove it.")
print("Safety check passed: approved isolated AVD; no saved auth or financial recovery detected.")
if args.snapshot:
    top = adb("shell", "dumpsys", "activity", "activities").stdout
    if "mResumedActivity" not in top or "HumanAcceptanceActivity" not in top:
        sys.exit("STOP: open the guarded human review host first; snapshot must not start an unreviewed session.")
    print(adb("shell", "am", "broadcast", "-n", package + "/.acceptance.AcceptanceSnapshotReceiver", "--ez", "isolatedAcceptance", "true").stdout)
else:
    print(adb("shell", "am", "start", "-W", "-n", package + "/.acceptance.HumanAcceptanceActivity", "--ez", "isolatedAcceptance", "true").stdout)
    print("Require the ISOLATED ACCEPTANCE banner. All fixture edits are process-memory only.")
