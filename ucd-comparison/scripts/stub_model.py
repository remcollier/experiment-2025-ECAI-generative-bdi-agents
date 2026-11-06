"""A stub OpenAI-compatible model server for checking prompts without a model (JaKtA comparison, stage 2).
Every chat completion request is appended to <log> as one JSON line (the request's messages), and answered
with a fixed reply that contains no plans or rules, so that no generated plan ever runs.

    python3 -I stub_model.py <port> <log>
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT, LOG = int(sys.argv[1]), sys.argv[2]
REPLY = '{"rules": []}'


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        with open(LOG, "a") as f:
            f.write(json.dumps({"path": self.path, "model": body.get("model"), "messages": body.get("messages")}) + "\n")
        out = json.dumps({
            "id": "stub", "object": "chat.completion", "created": 0, "model": body.get("model", "stub"),
            "choices": [{"index": 0, "message": {"role": "assistant", "content": REPLY}, "finish_reason": "stop"}],
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
