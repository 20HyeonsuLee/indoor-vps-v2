#!/usr/bin/env python3
"""Minimal stdin/stdout bridge for Java-owned HTTP runtime.

The Java server owns API routing and validation. Commands implemented here are
the compatibility boundary for Python-only workloads such as SuperPoint,
RTAB-Map reprocess, and build-worker map generation.
"""
from __future__ import annotations

import asyncio
import json
import os
from pathlib import Path
import sys


COMMANDS = ("health", "localize", "merge_scan")


class BridgeContractError(ValueError):
    def __init__(self, message: str, detail: dict[str, object] | None = None) -> None:
        super().__init__(message)
        self.detail = detail or {}


class BridgeRuntimeError(RuntimeError):
    def __init__(
        self,
        code: str,
        message: str,
        detail: dict[str, object] | None = None,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.detail = detail or {}


def main() -> int:
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    try:
        payload = json.load(sys.stdin)
        return dispatch(command, payload)
    except BridgeContractError as exc:
        return fail("BRIDGE_VALIDATION_ERROR", str(exc), exc.detail)
    except BridgeRuntimeError as exc:
        return fail(exc.code, str(exc), exc.detail)
    except json.JSONDecodeError as exc:
        return fail("BRIDGE_INVALID_JSON", "stdin must be JSON", {"reason": str(exc)})


def dispatch(command: str, payload: dict[str, object]) -> int:
    if command == "health":
        print(json.dumps({"ok": True, "commands": list(COMMANDS)}))
        return 0
    if command == "localize":
        validate_localize(payload)
        if payload.get("contractOnly") is True:
            print_json({"ok": True, "command": command})
            return 0
        return localize(payload)
    if command == "merge_scan":
        validate_merge_scan(payload)
        if payload.get("contractOnly") is True:
            print_json({"ok": True, "command": command})
            return 0
        return merge_scan(payload)
    return fail("BRIDGE_UNKNOWN_COMMAND", f"unknown bridge command: {command}", {"commands": list(COMMANDS)})


def contract_or_not_implemented(command: str, payload: dict[str, object]) -> int:
    if payload.get("contractOnly") is True:
        print_json({"ok": True, "command": command})
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
    require_floor_maps(payload)


def validate_merge_scan(payload: dict[str, object]) -> None:
    require_string(payload, "floorId")
    require_string(payload, "scanId")
    require_string(payload, "outputDir")
    require_string_list(payload, "sourcePaths", min_items=1)


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


def require_floor_maps(payload: dict[str, object]) -> list[dict[str, object]]:
    value = payload.get("floorMaps")
    if not isinstance(value, list) or not value:
        raise BridgeContractError("floorMaps must be a non-empty list", {"field": "floorMaps"})
    for index, item in enumerate(value):
        if not isinstance(item, dict):
            raise BridgeContractError(
                "floorMaps items must be objects",
                {"field": "floorMaps", "index": index},
            )
        if not isinstance(item.get("filePath"), str) or not item["filePath"]:
            raise BridgeContractError(
                "floorMaps[].filePath must be a non-empty string",
                {"field": "floorMaps.filePath", "index": index},
            )
    return value


def localize(payload: dict[str, object]) -> int:
    result = asyncio.run(localize_async(payload))
    print_json(result)
    return 0


async def localize_async(payload: dict[str, object]) -> dict[str, object]:
    get_engine, resize_query_images = load_legacy_localize_dependencies()
    image_bytes_list = read_image_bytes(payload["imagePaths"])
    floor_maps = payload["floorMaps"]
    slam_engine = await asyncio.to_thread(get_engine)
    intrinsics = extract_first_intrinsics(slam_engine, floor_maps)
    resized_images = resize_query_images(
        image_bytes_list,
        width=int(intrinsics["width"]),
        height=int(intrinsics["height"]),
    )

    async def localize_floor(floor_map: dict[str, object]) -> dict[str, object] | None:
        try:
            result = await slam_engine.localize(
                str(floor_map.get("floorId") or payload["buildingId"]),
                resized_images,
                intrinsics=intrinsics,
                db_path=str(floor_map["filePath"]),
                mask_persons=bool(payload.get("maskPersons", False)),
            )
            return {
                **result,
                "floor_id": str(floor_map.get("floorId") or ""),
                "floor_name": str(floor_map.get("floorName") or ""),
                "floor_level": int(floor_map.get("level") or 0),
            }
        except (FileNotFoundError, ValueError):
            return None

    results = await asyncio.gather(*(localize_floor(floor_map) for floor_map in floor_maps))
    valid = [result for result in results if result is not None]
    if not valid:
        raise BridgeRuntimeError(
            "BRIDGE_LOCALIZE_FAILED",
            "Localization failed on all floors",
            {"floorCount": len(floor_maps)},
        )

    best = max(valid, key=lambda result: (int(result.get("num_matches", 0)), float(result["confidence"])))
    return {
        "pose": best["pose"],
        "confidence": float(best["confidence"]),
        "mapId": str(payload["buildingId"]),
        "numMatches": int(best.get("num_matches", 0)),
        "matchedImageIndex": int(best.get("matched_image_index", 0)),
        "floorId": str(best.get("floor_id", "")),
        "floorLevel": int(best.get("floor_level", 0)),
    }


def load_legacy_localize_dependencies():
    backend_src = os.environ.get("INDOOR_LEGACY_BACKEND_SRC") or os.environ.get("PYTHON_BACKEND_SRC", "")
    if not backend_src:
        raise BridgeRuntimeError(
            "BRIDGE_BACKEND_NOT_CONFIGURED",
            "INDOOR_LEGACY_BACKEND_SRC is required for localize",
        )
    backend_path = Path(backend_src).expanduser().resolve()
    if not backend_path.exists():
        raise BridgeRuntimeError(
            "BRIDGE_BACKEND_NOT_CONFIGURED",
            "legacy Python backend source path does not exist",
            {"path": str(backend_path)},
        )
    sys.path.insert(0, str(backend_path))
    try:
        from indoor_server.application.slam.localize_service import (  # type: ignore
            _get_sp_engine,
            _resize_query_images,
        )
    except Exception as exc:
        raise BridgeRuntimeError(
            "BRIDGE_BACKEND_IMPORT_FAILED",
            str(exc),
            {"path": str(backend_path), "type": type(exc).__name__},
        ) from exc
    return _get_sp_engine, _resize_query_images


def extract_first_intrinsics(slam_engine, floor_maps: list[dict[str, object]]) -> dict[str, object]:
    errors: list[str] = []
    for floor_map in floor_maps:
        try:
            return slam_engine.extract_intrinsics_from_db(str(floor_map["filePath"]))
        except Exception as exc:
            errors.append(f"{floor_map['filePath']}: {exc}")
    raise BridgeRuntimeError(
        "BRIDGE_INTRINSICS_UNAVAILABLE",
        "Failed to extract intrinsics from any floor DB",
        {"errors": errors},
    )


def merge_scan(payload: dict[str, object]) -> int:
    result = asyncio.run(merge_scan_async(payload))
    print_json(result)
    return 0


async def merge_scan_async(payload: dict[str, object]) -> dict[str, object]:
    if len(payload["sourcePaths"]) < 2:
        raise BridgeRuntimeError(
            "BRIDGE_MERGE_REQUIRES_SOURCES",
            "merge_scan requires at least two sourcePaths",
        )
    runner_class, params_class, source_class, merge_error_class = load_legacy_merge_dependencies()
    output_dir = Path(str(payload["outputDir"]))
    output_db = output_dir / "rtabmap.db"
    work_dir = output_dir / "_merge_work"
    sources = [
        source_class(scan_id=Path(path).parent.name, db_path=Path(path))
        for path in payload["sourcePaths"]
    ]
    runner = runner_class()
    if not runner.is_available():
        raise BridgeRuntimeError(
            "RTABMAP_REPROCESS_UNAVAILABLE",
            "rtabmap-reprocess binary not available",
        )
    try:
        result = await runner.run(
            sources=sources,
            output_db=output_db,
            work_dir=work_dir,
            params=params_class(),
            timeout_s=float(payload.get("timeoutSeconds") or 900.0),
        )
    except merge_error_class as exc:
        raise BridgeRuntimeError(
            "RTABMAP_REPROCESS_FAILED",
            str(exc),
            {"sourceCount": len(sources)},
        ) from exc
    return {
        "mergedDbPath": str(output_db),
        "sha256": sha256_file(output_db),
        "fileSize": output_db.stat().st_size,
        "diagnostics": result.to_metadata(),
    }


def load_legacy_merge_dependencies():
    backend_src = os.environ.get("INDOOR_LEGACY_BACKEND_SRC") or os.environ.get("PYTHON_BACKEND_SRC", "")
    if not backend_src:
        raise BridgeRuntimeError(
            "BRIDGE_BACKEND_NOT_CONFIGURED",
            "INDOOR_LEGACY_BACKEND_SRC is required for merge_scan",
        )
    backend_path = Path(backend_src).expanduser().resolve()
    if not backend_path.exists():
        raise BridgeRuntimeError(
            "BRIDGE_BACKEND_NOT_CONFIGURED",
            "legacy Python backend source path does not exist",
            {"path": str(backend_path)},
        )
    sys.path.insert(0, str(backend_path))
    try:
        from indoor_server.application.building.multiscan_rtabmap_merge import (  # type: ignore
            MultiScanReprocessParams,
            MultiScanRtabmapMergeError,
            MultiScanRtabmapReprocessRunner,
            SourceRtabmapScan,
        )
    except Exception as exc:
        raise BridgeRuntimeError(
            "BRIDGE_BACKEND_IMPORT_FAILED",
            str(exc),
            {"path": str(backend_path), "type": type(exc).__name__},
        ) from exc
    return (
        MultiScanRtabmapReprocessRunner,
        MultiScanReprocessParams,
        SourceRtabmapScan,
        MultiScanRtabmapMergeError,
    )


def read_image_bytes(image_paths: list[str]) -> list[bytes]:
    images: list[bytes] = []
    for index, image_path in enumerate(image_paths):
        path = Path(image_path)
        try:
            data = path.read_bytes()
        except OSError as exc:
            raise BridgeRuntimeError(
                "BRIDGE_IMAGE_READ_FAILED",
                str(exc),
                {"index": index, "path": str(path)},
            ) from exc
        if not data:
            raise BridgeRuntimeError(
                "BRIDGE_IMAGE_READ_FAILED",
                "query image file is empty",
                {"index": index, "path": str(path)},
            )
        images.append(data)
    return images


def sha256_file(path: Path) -> str:
    import hashlib

    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fail(code: str, message: str, detail: dict[str, object] | None = None) -> int:
    print(
        json.dumps({"error": {"code": code, "message": message, "detail": detail or {}}}, default=json_default),
        file=sys.stderr,
    )
    return 2


def print_json(payload: dict[str, object]) -> None:
    print(json.dumps(payload, default=json_default))


def json_default(value):
    if hasattr(value, "item"):
        return value.item()
    if isinstance(value, Path):
        return str(value)
    raise TypeError(f"Object of type {type(value).__name__} is not JSON serializable")


if __name__ == "__main__":
    raise SystemExit(main())
