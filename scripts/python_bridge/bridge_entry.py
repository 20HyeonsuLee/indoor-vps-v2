#!/usr/bin/env python3
"""Minimal stdin/stdout bridge for Java-owned HTTP runtime.

The Java server owns API routing and validation. Commands implemented here are
the compatibility boundary for Python-only workloads such as SuperPoint,
RTAB-Map reprocess, and build-worker map generation.
"""
from __future__ import annotations

import json
import sys


COMMANDS = ("health", "localize", "merge_scan", "build_floor_map")


class BridgeContractError(ValueError):
    def __init__(self, message: str, detail: dict[str, object] | None = None) -> None:
        super().__init__(message)
        self.detail = detail or {}


def main() -> int:
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    try:
        payload = json.load(sys.stdin)
        return dispatch(command, payload)
    except BridgeContractError as exc:
        return fail("BRIDGE_VALIDATION_ERROR", str(exc), exc.detail)
    except json.JSONDecodeError as exc:
        return fail("BRIDGE_INVALID_JSON", "stdin must be JSON", {"reason": str(exc)})


def dispatch(command: str, payload: dict[str, object]) -> int:
    if command == "health":
        print(json.dumps({"ok": True, "commands": list(COMMANDS)}))
        return 0
    if command == "localize":
        validate_localize(payload)
        return contract_or_not_implemented(command, payload)
    if command == "merge_scan":
        validate_merge_scan(payload)
        return contract_or_not_implemented(command, payload)
    if command == "build_floor_map":
        validate_build_floor_map(payload)
        return contract_or_not_implemented(command, payload)
    return fail("BRIDGE_UNKNOWN_COMMAND", f"unknown bridge command: {command}", {"commands": list(COMMANDS)})


def contract_or_not_implemented(command: str, payload: dict[str, object]) -> int:
    if payload.get("contractOnly") is True:
        print(json.dumps({"ok": True, "command": command}))
        return 0
    return fail(
        "BRIDGE_COMMAND_NOT_IMPLEMENTED",
        f"bridge command is not implemented yet: {command}",
        {"command": command},
    )


def validate_localize(payload: dict[str, object]) -> None:
    require_string(payload, "buildingId")
    require_string(payload, "storageRoot")
    require_string_list(payload, "imagePaths", min_items=1)


def validate_merge_scan(payload: dict[str, object]) -> None:
    require_string(payload, "floorId")
    require_string(payload, "scanId")
    require_string(payload, "outputDir")
    require_string_list(payload, "sourcePaths", min_items=1)


def validate_build_floor_map(payload: dict[str, object]) -> None:
    require_string(payload, "floorId")
    require_string(payload, "scanId")
    require_string(payload, "buildJobId")
    require_string(payload, "scanPath")
    require_string(payload, "outputDir")


def require_string(payload: dict[str, object], key: str) -> str:
    value = payload.get(key)
    if not isinstance(value, str) or not value:
        raise BridgeContractError(f"{key} must be a non-empty string", {"field": key})
    return value


def require_string_list(payload: dict[str, object], key: str, min_items: int) -> list[str]:
    value = payload.get(key)
    if not isinstance(value, list) or len(value) < min_items or not all(isinstance(item, str) and item for item in value):
        raise BridgeContractError(f"{key} must be a non-empty string list", {"field": key})
    return value


def fail(code: str, message: str, detail: dict[str, object] | None = None) -> int:
    print(
        json.dumps({"error": {"code": code, "message": message, "detail": detail or {}}}),
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
