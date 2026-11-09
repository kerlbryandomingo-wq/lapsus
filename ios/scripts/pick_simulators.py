#!/usr/bin/env python3
"""Prints SIM_* lines for $GITHUB_ENV: the simulators CI tests on, from the newest installed iOS runtime.

  SIM_PRIMARY  an iPhone for unit tests
  SIM_UI       space-separated UDIDs for UI tests: an iPhone and an iPad, plus (mode "all") a small
               iPhone and an iPad mini
"""
import json, re, subprocess, sys

mode = sys.argv[1] if len(sys.argv) > 1 else "default"
data = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "devices", "available", "-j"]))


def version(runtime):
    m = re.search(r"iOS-(\d+)-(\d+)", runtime)
    return (int(m.group(1)), int(m.group(2))) if m else (0, 0)


runtimes = sorted((r for r in data["devices"] if "iOS" in r and data["devices"][r]), key=version, reverse=True)
if not runtimes:
    sys.exit("No iOS simulators are installed")
devices = data["devices"][runtimes[0]]
names = {d["name"]: d["udid"] for d in devices}
print(f"# runtime {runtimes[0]}: {', '.join(sorted(names))}", file=sys.stderr)


def pick(patterns, exclude=()):
    for p in patterns:
        for name in sorted(names, reverse=True):
            if re.search(p, name) and not any(re.search(e, name) for e in exclude):
                return name
    return None


iphone = pick([r"^iPhone \d+ Pro$", r"^iPhone \d+$", r"^iPhone"], exclude=[r"SE", r"e$"])
ipad = pick([r"^iPad Pro 13", r"^iPad Pro", r"^iPad Air", r"^iPad"], exclude=[r"mini"])
chosen = [iphone, ipad]
if mode == "all":
    chosen += [pick([r"^iPhone SE", r"^iPhone \d+e$", r"^iPhone \d+ mini"]), pick([r"^iPad mini"])]
chosen = [c for c in dict.fromkeys(chosen) if c]
if not iphone:
    sys.exit("No iPhone simulator found")
print(f"SIM_PRIMARY={names[iphone]}")
print("SIM_UI=" + " ".join(names[c] for c in chosen))
print("SIM_NAMES=" + "; ".join(chosen))
