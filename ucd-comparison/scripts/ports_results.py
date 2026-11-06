"""JaKtA comparison, stage 2: judges runs of the tasks ported to JaKtA (rooms, tea, keys, print) as ASTRA's
harness judges its runs: a run succeeds if the robot's goal ends, not by failing, while the task's check holds.

    python3 -I scripts/jakta/ports/ports_results.py <folder of run folders> [more folders]

A run folder holds the run's logs (--log-dir/<run id>). The environment logs "Task check: true|false"
whenever the check changes (the MAS log); the robot's log says when its intention ends ("Removed intention").
Outcome of a run:
  success     the intention ended, not by failing, and the last check logged by then was true
  not achieved  it ended, not by failing, with the check false (the plans finished short of the goal)
  failed      it ended by failing (a fail action, an action that failed, or no applicable plan)
  timeout     it never ended before the run's limit
  no start    no robot log (an infrastructure fault, not a model's failure)
Prints one line per run (TSV) with its generations, then a summary by folder.
"""
import collections
import glob
import json
import os
import re
import sys
from datetime import datetime

ROBOT_LOG = re.compile(r"Mas-[0-9a-f-]{36}-Robot-[0-9a-f-]{36}\.log$")
MAS_LOG = re.compile(r"Mas-[0-9a-f-]{36}\.log$")
CHAT = re.compile(r"-Robot-[0-9a-f-]{36}-[a-z]+_[a-z]+-[0-9a-f-]+\.jsonl$")
FAILURE = re.compile(r'"fail" was successfully executed|plans are not applicable|could not be applied|failed with the given'
                     r"|No plan found for trigger achieve failure"
                     # stage 3, J+R: a generation refused at the cap (never in stage 2's logs)
                     r"|Stage 3 generation refused")


def stamp(line):
    try:
        return datetime.strptime(line[:26], "%Y-%m-%d %H:%M:%S.%f")
    except ValueError:
        return None


def judge(folder):
    files = os.listdir(folder)
    robot = [f for f in files if ROBOT_LOG.search(f)]
    mas = [f for f in files if MAS_LOG.fullmatch(f)]
    generations = 0
    for f in files:
        if CHAT.search(f):
            for line in open(os.path.join(folder, f), errors="replace"):
                try:
                    m = json.loads(line).get("message")
                except ValueError:
                    continue
                if isinstance(m, dict) and m.get("event", {}).get("type") == "LMMessageReceived":
                    generations += 1
    if not robot:
        return "no start", generations
    lines = open(os.path.join(folder, robot[0]), errors="replace").read().splitlines()
    removed = next((i for i, l in enumerate(lines) if "Removed intention" in l), None)
    if removed is None:
        return "timeout", generations
    if FAILURE.search("\n".join(lines[max(0, removed - 40):removed])):
        return "failed", generations
    ended = stamp(lines[removed])
    check = None
    if mas:
        for line in open(os.path.join(folder, mas[0]), errors="replace"):
            if "Task check:" in line:
                t = stamp(line)
                if t is not None and ended is not None and t <= ended:
                    check = line.rstrip().endswith("true")
    return ("success" if check else "not achieved"), generations


def main():
    summary = collections.defaultdict(collections.Counter)
    print("folder\trun\toutcome\tgenerations")
    for out in sys.argv[1:]:
        for folder in sorted(glob.glob(os.path.join(out, "*", ""))):
            run = os.path.basename(os.path.dirname(folder))
            outcome, generations = judge(folder)
            print(f"{out}\t{run}\t{outcome}\t{generations}")
            summary[out][outcome] += 1
    for out, c in summary.items():
        print(f"# {out}: " + ", ".join(f"{k} {v}" for k, v in sorted(c.items())), file=sys.stderr)


if __name__ == "__main__":
    main()
