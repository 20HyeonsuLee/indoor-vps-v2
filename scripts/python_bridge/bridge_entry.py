#!/usr/bin/env python3
"""Minimal stdin/stdout bridge for Java-owned HTTP runtime.

The Java server owns API routing and validation. Commands implemented here are
the compatibility boundary for Python-only workloads such as SuperPoint,
RTAB-Map reprocess, and build-worker map generation.
"""
from __future__ import annotations

import json
import sys


def main() -> int:
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    _payload = json.load(sys.stdin)
    if command == "health":
        print(json.dumps({"ok": True}))
        return 0
    print(json.dumps({"error": f"command not implemented: {command}"}), file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
