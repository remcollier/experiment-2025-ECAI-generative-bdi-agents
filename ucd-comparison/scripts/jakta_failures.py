"""JaKtA calibration (stage 1): how each JaKtA run ended, coded from its logs, and what its plans did
with the helper. Reads the run folders that run-jakta.sh or jakta.sbatch writes (runs.tsv beside them).

    python3 -I scripts/jakta/jakta_failures.py <JaKtA output folder> [more folders]

Outcome of a run (first that applies):
  success      the environment logged "Reached object home"
  no plans     no generation produced a plan
  stopped      the plans executed stop (the agent's controller stopped) away from home
  failed       the intention ended after a fail action or with no applicable plan
  ended        the intention ended without failing: the plans finished away from home (no recursion)
  wandering    still running at the limit, and still moving in the run's last quarter
  stuck        still running at the limit, but no move in the run's last quarter
Plan features (from every reply of the run): the helper (getDirectionToMove) used at all; used with its
direction already bound by the plan's conditions (free(D) or there_is(_, D) before the call), so it
chooses nothing; the helper treated as a goal (achieve getDirectionToMove, with plans of the model's own);
plans that adopt reach again (recursion). Operations may carry their keyword in brackets, as gpt-oss
copies it from the system prompt ("[achieve] reach(home)"); JaKtA parses that as the keyword.
"""
import collections
import csv
import glob
import json
import os
import re
import sys
from datetime import datetime

AGENT_LOG = re.compile(r"Mas-[0-9a-f-]{36}-ExplorerRobot-[0-9a-f-]{36}\.log$")
CHAT = re.compile(r"-[a-z]+_[a-z]+-[0-9a-f-]+\.jsonl$")
LABELS = {"PROMPT_WITHOUT_HINTS": "no hints", "PROMPT_WITH_HINTS": "hints",
          "PROMPT_WITH_HINTS_AND_REMARKS": "remarks"}
OUTCOMES = ["success", "no plans", "stopped", "failed", "ended", "wandering", "stuck"]


def stamp(line):
    try:
        return datetime.strptime(line[:26], "%Y-%m-%d %H:%M:%S.%f")
    except ValueError:
        return None


def replies(folder):
    out = []
    for f in glob.glob(os.path.join(folder, "*.jsonl")):
        if not CHAT.search(f):
            continue
        for line in open(f, errors="replace"):
            try:
                m = json.loads(line).get("message")
            except ValueError:
                continue
            if isinstance(m, dict) and m.get("event", {}).get("type") == "LMMessageReceived":
                out.append(m["event"]["chatMessage"].get("content") or "")
    return out


def plans(reply):
    """[(event, conditions, operations)] from the YAML plans of a reply."""
    found = []
    for block in re.split(r"\n\s*---\s*\n|```", reply):
        ev = re.search(r"EVENT:\s*(.+)", block)
        if not ev:
            continue
        cond = re.search(r"CONDITIONS:(.*?)(?:OPERATIONS:|$)", block, re.S)
        ops = re.search(r"OPERATIONS:(.*)", block, re.S)
        found.append((ev.group(1).strip(), cond.group(1) if cond else "", ops.group(1) if ops else ""))
    return found


def features(texts):
    used = bound = recursion = as_goal = 0
    n = 0
    for text in texts:
        for event, cond, ops in plans(text):
            n += 1
            for arg in re.findall(r"getDirectionToMove\(\s*([^)\s]*)\s*\)", ops):
                used = 1
                # Bound already: a constant, or a variable the conditions bind positively
                positive = "\n".join(l for l in cond.splitlines() if not re.search(r"\bnot\b|~", l))
                if not arg[:1].isupper() or re.search(r"\b" + re.escape(arg) + r"\b", positive):
                    bound = 1
            if re.search(r"\[?achieve\]?\s+reach\(", ops):
                recursion = 1
            if re.search(r"\[?achieve\]?\s+getDirectionToMove", event + ops):
                as_goal = 1
    return {"plans": n, "helper": used, "helper bound": bound, "helper as goal": as_goal, "recursion": recursion}


def outcome(folder, texts, n_plans):
    logs = [f for f in glob.glob(os.path.join(folder, "*.log")) if AGENT_LOG.search(f)]
    lines = open(logs[0], errors="replace").read().splitlines() if logs else []
    text = "\n".join(lines)
    if "Reached object home" in text or any("Reached object home" in open(f, errors="replace").read()
                                            for f in glob.glob(os.path.join(folder, "*.log"))):
        return "success"
    if n_plans == 0:
        return "no plans"
    if "entered stop state" in text:
        return "stopped"
    removed = [i for i, l in enumerate(lines) if "Removed intention" in l]
    if removed:
        before = "\n".join(lines[max(0, removed[-1] - 40):removed[-1]])
        if '"fail" was successfully executed' in before or "plans are not applicable" in before:
            return "failed"
        return "ended"
    times = [t for t in (stamp(l) for l in lines) if t]
    moves = [stamp(l) for l in lines if "Moved from Cell" in l]
    if times and moves and moves[-1] and moves[-1] >= times[0] + 0.75 * (times[-1] - times[0]):
        return "wandering"
    return "stuck"


def main():
    rows = []
    for out in sys.argv[1:]:
        for tsv in glob.glob(os.path.join(out, "**", "runs.tsv"), recursive=True):
            base = os.path.dirname(tsv)
            for r in csv.DictReader(open(tsv), delimiter="\t"):
                folder = os.path.join(base, r["run"])
                if not os.path.isdir(folder):
                    continue
                texts = replies(folder)
                f = features(texts)
                rows.append({"model": r["model"], "prompt": LABELS.get(r["prompt"], r["prompt"]),
                             "outcome": outcome(folder, texts, f["plans"]), **f})
    for model in sorted({r["model"] for r in rows}):
        print(f"\n## {model}\n")
        print("| Prompt | " + " | ".join(OUTCOMES) + " | helper used | helper bound | helper as goal | recursion |")
        print("|---" * (len(OUTCOMES) + 5) + "|")
        for prompt in ("no hints", "hints", "remarks"):
            cell = [r for r in rows if r["model"] == model and r["prompt"] == prompt]
            if not cell:
                continue
            c = collections.Counter(r["outcome"] for r in cell)
            print(f"| {prompt} | " + " | ".join(str(c[o]) for o in OUTCOMES)
                  + f" | {sum(r['helper'] for r in cell)} | {sum(r['helper bound'] for r in cell)}"
                  f" | {sum(r['helper as goal'] for r in cell)} | {sum(r['recursion'] for r in cell)} |")
        # Among failures, the helper bound against not
        fails = [r for r in rows if r["model"] == model and r["outcome"] != "success" and r["helper"]]
        wins = [r for r in rows if r["model"] == model and r["outcome"] == "success"]
        print(f"\nHelper bound by the conditions: {sum(r['helper bound'] for r in fails)} of {len(fails)} failed runs that use it;"
              f" {sum(r['helper bound'] for r in wins)} of {len(wins)} successful runs.")


if __name__ == "__main__":
    main()
