"""JaKtA comparison, stage 2: what ended each JaKtA port run that ports_results.py codes as *failed*,
from the 40 lines of the robot's log before its intention was removed: a fail action, no applicable plan
(plans exist but none applies, so JaKtA does not generate again), a plan that could not be applied, or an
action that failed. Prints the count per task and cause, with one example line.

    python3 -I scripts/jakta/ports/ports_failcauses.py <JaKtA output folder> [more folders]
"""
import os, re, sys, glob, collections
ROBOT = re.compile(r"Mas-[0-9a-f-]{36}-Robot-[0-9a-f-]{36}\.log$")
PATS = [("fail action", r'"fail" was successfully executed'), ("no applicable plan", r"plans are not applicable|No plan found for trigger achieve failure"),
        ("plan could not be applied", r"could not be applied"), ("action failed", r"failed with the given")]
c = collections.Counter(); ex = {}
for out in sys.argv[1:]:
    for d in sorted(glob.glob(os.path.join(out, "*", ""))):
        task = re.search(r"-(rooms|tea|keys|print)-t", d).group(1)
        r = [f for f in os.listdir(d) if ROBOT.search(f)]
        if not r: continue
        lines = open(os.path.join(d, r[0]), errors="replace").read().splitlines()
        i = next((k for k, l in enumerate(lines) if "Removed intention" in l), None)
        if i is None: continue
        win = "\n".join(lines[max(0, i - 40):i])
        hits = [n for n, p in PATS if re.search(p, win)]
        if hits:
            key = (task, "+".join(hits)); c[key] += 1
            if key not in ex:
                m = [l for l in lines[max(0, i - 40):i] if any(re.search(p, l) for _, p in PATS)]
                ex[key] = m[-1][27:330] if m else ""
for k, v in sorted(c.items()): print(v, k, "|", ex[k])
