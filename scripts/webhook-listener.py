#!/usr/bin/env python3
"""Tiny webhook consumer for local testing.

Prints every delivery's signature header and body to stdout.
Usage: python3 scripts/webhook-listener.py [port]  (default 9000)
"""
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer


class Hook(BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers["Content-Length"])).decode()
        print("signature:", self.headers.get("X-EventCore-Signature"), flush=True)
        print("body:", body, flush=True)
        self.send_response(200)
        self.end_headers()

    def log_message(self, *args):
        pass


port = int(sys.argv[1]) if len(sys.argv) > 1 else 9000
print(f"listening on :{port} — POST deliveries will be printed here", flush=True)
HTTPServer(("", port), Hook).serve_forever()
