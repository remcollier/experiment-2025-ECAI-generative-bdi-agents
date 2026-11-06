"""JaKtA comparison, stage 2: every JaKtA port run's model replies in time order, as JSON lines
{run, replies: [text]}, for stage2_plan_styles.py. Run where the JaKtA outputs are (agentic, Sonic).

    python3 -I scripts/jakta/ports/extract_replies.py <JaKtA output folder> [more] > replies.jsonl

Kept in reports/2026-11-04-jakta-s2-jakta-replies/ with the ports_results.py judgements, so the analysis
can be rerun without the raw logs.
"""
import glob, json, os, re, sys
CHAT = re.compile(r"-Robot-[0-9a-f-]{36}-[a-z]+_[a-z]+-[0-9a-f-]+\.jsonl$")
for out in sys.argv[1:]:
    for d in sorted(glob.glob(os.path.join(out, "*", ""))):
        got = []
        for f in os.listdir(d):
            if not CHAT.search(f):
                continue
            for line in open(os.path.join(d, f), errors="replace"):
                try:
                    j = json.loads(line)
                except ValueError:
                    continue
                m = j.get("message")
                if isinstance(m, dict) and m.get("event", {}).get("type") == "LMMessageReceived":
                    got.append((j["@timestamp"], m["event"]["chatMessage"].get("content") or ""))
        got.sort()
        print(json.dumps({"run": os.path.basename(os.path.dirname(d)), "replies": [t for _, t in got]}))
