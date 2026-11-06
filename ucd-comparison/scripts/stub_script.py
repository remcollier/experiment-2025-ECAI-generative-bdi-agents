"""A scripted stub OpenAI-compatible model server (JaKtA comparison, stage 3: checks of J+R without a model).
Answers the n-th chat request with the n-th reply of a JSON list (the last one again after the list ends), and
appends each request to <log> as a JSON line.

    python3 -I stub_script.py <port> <log> <replies.json>
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT, LOG, REPLIES = int(sys.argv[1]), sys.argv[2], json.load(open(sys.argv[3]))
count = [0]


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        reply = REPLIES[min(count[0], len(REPLIES) - 1)]
        count[0] += 1
        with open(LOG, "a") as f:
            f.write(json.dumps({"n": count[0], "messages": body.get("messages")}) + "\n")
        out = json.dumps({
            "id": "stub", "object": "chat.completion", "created": 0, "model": body.get("model", "stub"),
            "choices": [{"index": 0, "message": {"role": "assistant", "content": reply}, "finish_reason": "stop"}],
            "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0},
        }).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(out)))
        self.end_headers()
        self.wfile.write(out)

    def log_message(self, *args):
        pass


HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
