#!/usr/bin/env python3
"""A complete EventCore pull consumer in one file, standard library only.

Creates (or resumes) a named durable cursor, then runs the crash-safe
fetch -> process -> commit loop. Kill it at any moment and restart:
it resumes exactly where it left off, never losing an event.

Usage:
    export EVENTCORE_URL=http://localhost:8080
    export EVENTCORE_API_KEY=ek_...           # issue one via POST /v1/api-keys
    python3 consumer.py [consumer-name] [beginning|now|ISO-timestamp]
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE = os.environ.get("EVENTCORE_URL", "http://localhost:8080")
API_KEY = os.environ["EVENTCORE_API_KEY"]
NAME = sys.argv[1] if len(sys.argv) > 1 else "python-demo"
FROM = sys.argv[2] if len(sys.argv) > 2 else "beginning"


def call(method, path, body=None):
    request = urllib.request.Request(
        BASE + path,
        method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"X-API-Key": API_KEY, "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request) as response:
            return response.status, json.loads(response.read() or "{}")
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read() or "{}")


def ensure_subscription():
    status, body = call("POST", "/v1/pull-subscriptions", {"name": NAME, "from": FROM})
    if status == 201:
        print(f"created pull subscription '{NAME}' from {FROM}")
    elif status == 409:
        print(f"resuming existing pull subscription '{NAME}'")
    else:
        sys.exit(f"could not create subscription: {status} {body}")


def process(event):
    """Your business logic goes here. Must be idempotent (at-least-once delivery)."""
    print(f"  {event['time']}  {event['type']}  payload={json.dumps(event.get('payload'))}")


def run():
    ensure_subscription()
    while True:
        _, batch = call("GET", f"/v1/pull-subscriptions/{NAME}/events?limit=100")
        if not batch["items"]:
            time.sleep(2)  # caught up; poll politely
            continue
        for event in batch["items"]:
            process(event)
        # Commit only after the whole batch is processed: crash before this
        # line and the batch is re-fetched on restart (at-least-once).
        call("POST", f"/v1/pull-subscriptions/{NAME}/commit", {"cursor": batch["nextCursor"]})
        print(f"committed {len(batch['items'])} events")


if __name__ == "__main__":
    try:
        run()
    except KeyboardInterrupt:
        print("\nstopped; position is committed - restart to resume")
