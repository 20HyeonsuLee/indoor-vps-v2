#!/usr/bin/env python3
"""Minimal stdin/stdout bridge for Java-owned HTTP runtime.

The Java server owns API routing and validation. Commands implemented here are
the compatibility boundary for Python-only workloads such as SuperPoint and
RTAB-Map reprocess.
"""
from __future__ import annotations

import asyncio
import json
import os
from pathlib import Path
import sys


COMMANDS = (
    "health",
    "localize",
    "merge_scan",
    "build_superpoint_index",
    "export_pointcloud",
)


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
    if command == "daemon":
        return daemon_loop()
    try:
        payload = json.load(sys.stdin)
        result = dispatch(command, payload)
        print_json(result)
        return 0
    except BridgeContractError as exc:
        return fail("BRIDGE_VALIDATION_ERROR", str(exc), exc.detail)
    except BridgeRuntimeError as exc:
        return fail(exc.code, str(exc), exc.detail)
    except json.JSONDecodeError as exc:
        return fail("BRIDGE_INVALID_JSON", "stdin must be JSON", {"reason": str(exc)})


def dispatch(command: str, payload: dict[str, object]) -> dict[str, object]:
    if command == "health":
        return health_response()
    if command == "localize":
        validate_localize(payload)
        if payload.get("contractOnly") is True:
            return {"ok": True, "command": command}
        return localize(payload)
    if command == "merge_scan":
        validate_merge_scan(payload)
        if payload.get("contractOnly") is True:
            return {"ok": True, "command": command}
        return merge_scan(payload)
    if command == "build_superpoint_index":
        validate_build_superpoint_index(payload)
        if payload.get("contractOnly") is True:
            return {"ok": True, "command": command}
        return build_superpoint_index(payload)
    if command == "export_pointcloud":
        validate_export_pointcloud(payload)
        if payload.get("contractOnly") is True:
            return {"ok": True, "command": command}
        return export_pointcloud(payload)
    raise BridgeRuntimeError(
        "BRIDGE_UNKNOWN_COMMAND",
        f"unknown bridge command: {command}",
        {"commands": list(COMMANDS)},
    )


def health_response() -> dict[str, object]:
    return {
        "ok": True,
        "commands": list(COMMANDS),
        "mlDevice": requested_ml_device(),
        "cudaVisibleDevices": os.environ.get("CUDA_VISIBLE_DEVICES", "<unset>"),
    }


def daemon_loop() -> int:
    write_line({"event": "ready", "commands": list(COMMANDS)})
    for raw_line in sys.stdin:
        line = raw_line.strip()
        if not line:
            continue
        write_line(handle_daemon_line(line))
    return 0


def handle_daemon_line(line: str) -> dict[str, object]:
    req_id: object = None
    try:
        msg = json.loads(line)
        req_id = msg.get("id")
        command = str(msg.get("command", ""))
        payload = msg.get("payload") or {}
        if not isinstance(payload, dict):
            raise BridgeContractError(
                "payload must be a JSON object",
                {"got": type(payload).__name__},
            )
        result = dispatch(command, payload)
        return {"id": req_id, "ok": True, "data": result}
    except BridgeContractError as exc:
        return daemon_error(req_id, "BRIDGE_VALIDATION_ERROR", str(exc), exc.detail)
    except BridgeRuntimeError as exc:
        return daemon_error(req_id, exc.code, str(exc), exc.detail)
    except json.JSONDecodeError as exc:
        return daemon_error(req_id, "BRIDGE_INVALID_JSON", str(exc), {})
    except Exception as exc:
        return daemon_error(
            req_id,
            "BRIDGE_INTERNAL_ERROR",
            str(exc),
            {"type": type(exc).__name__},
        )


def daemon_error(
    req_id: object,
    code: str,
    message: str,
    detail: dict[str, object],
) -> dict[str, object]:
    return {
        "id": req_id,
        "ok": False,
        "error": {"code": code, "message": message, "detail": detail},
    }


def write_line(payload: dict[str, object]) -> None:
    sys.stdout.write(json.dumps(payload, default=json_default))
    sys.stdout.write("\n")
    sys.stdout.flush()


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


def localize(payload: dict[str, object]) -> dict[str, object]:
    return asyncio.run(localize_async(payload))


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

    # depthPaths optional, parallel to imagePaths. Empty string / None entries → no depth
    # for that image (server falls back to 2D-3D PnP).
    depth_paths = payload.get("depthPaths") or []
    depth_bytes_list: list[bytes | None] = []
    for i in range(len(image_bytes_list)):
        if i >= len(depth_paths):
            depth_bytes_list.append(None)
            continue
        p = depth_paths[i]
        if not p:
            depth_bytes_list.append(None)
            continue
        try:
            with open(str(p), "rb") as f:
                depth_bytes_list.append(f.read())
        except OSError:
            depth_bytes_list.append(None)

    async def localize_floor(floor_map: dict[str, object]) -> dict[str, object] | None:
        # Use areaId as the SuperPoint cache key so two areas under the same
        # floorId don't share/overwrite each other's index.
        cache_key = str(floor_map.get("areaId") or floor_map.get("floorId") or payload["buildingId"])
        try:
            result = await slam_engine.localize(
                cache_key,
                resized_images,
                intrinsics=intrinsics,
                db_path=str(floor_map["filePath"]),
                mask_persons=bool(payload.get("maskPersons", False)),
                depths=depth_bytes_list,
            )
            return {
                **result,
                "floor_id": str(floor_map.get("floorId") or ""),
                "area_id": str(floor_map.get("areaId") or ""),
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
        "numMatches": int(best.get("num_matches", 0)),
        "matchedImageIndex": int(best.get("matched_image_index", 0)),
        "methodUsed": str(best.get("method_used", "")),
        "floorId": str(best.get("floor_id", "")),
        "areaId": str(best.get("area_id", "")),
        "floorLevel": int(best.get("floor_level", 0)),
    }


def load_legacy_localize_dependencies():
    backend_path = resolve_legacy_backend_src("localize")
    prepend_sys_path(backend_path)
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


def merge_scan(payload: dict[str, object]) -> dict[str, object]:
    return asyncio.run(merge_scan_async(payload))


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

    # 머지(rtabmap-reprocess -a) 출력은 sub-map 경계 너머 loop closure를
    # 결과 DB에 저장하지 않는 quirk가 있음. 따라서 단일 DB로 다시 reprocess해
    # cross-map closure를 확보. 다만 graph optimization은 여전히 각 sub-map의
    # 첫 노드를 anchor로 고정하므로 sub-map B의 좌표는 옮기지 않음.
    reprocessed_db = output_dir / "rtabmap_reprocessed.db"
    reprocess_diag = await _post_merge_reprocess(
        binary_path=runner.binary_path,
        input_db=output_db,
        output_db=reprocessed_db,
        timeout_s=float(payload.get("timeoutSeconds") or 900.0),
    )

    # Stage 3: cross-session link로부터 SE(3) yaw-only alignment 추정 후
    # sub-map B 노드 pose를 직접 patch. rtabmap이 graph optimization으로
    # 풀지 못하는 sub-map 정렬을 후처리로 강제한다.
    pose_source_db = reprocessed_db if reprocessed_db.exists() else output_db
    alignment_diag = _align_submaps(pose_source_db)

    metadata_diag = _merge_metadata_alongside(
        source_paths=[Path(p) for p in payload["sourcePaths"]],
        merged_rtabmap_db=pose_source_db,
        output_dir=output_dir,
    )

    diagnostics = result.to_metadata()
    diagnostics["post_merge_reprocess"] = reprocess_diag
    diagnostics["submap_alignment"] = alignment_diag
    diagnostics["scan_metadata_merge"] = metadata_diag
    return {
        "mergedDbPath": str(output_db),
        "sha256": sha256_file(output_db),
        "fileSize": output_db.stat().st_size,
        "diagnostics": diagnostics,
    }


async def _post_merge_reprocess(
    *,
    binary_path: str | None,
    input_db: Path,
    output_db: Path,
    timeout_s: float,
) -> dict[str, object]:
    """단일 DB로 rtabmap-reprocess를 한 번 더 돌려 cross-map closure를 확보.

    `rtabmap-reprocess -a` (append mode)가 sub-map 경계 너머의 loop closure를
    결과 DB에 저장하지 않는 quirk를 우회한다. 단일 DB로 reprocess하면 경계가
    사라져 visual matching이 정상 작동, 추가 cross-map closure가 발견되며
    graph optimization으로 sub-map들이 통일 좌표계로 정렬된다.
    """
    if not binary_path:
        return {"status": "skipped", "reason": "rtabmap-reprocess binary unavailable"}
    if not input_db.exists():
        return {"status": "skipped", "reason": f"input db missing: {input_db}"}
    if output_db.exists():
        output_db.unlink()

    # `-a`/source 분리 없이 단일 DB 입력으로 호출. 머지 시 적용한 loop-closure
    # 친화 옵션을 그대로 유지(통일 후 graph optimization을 다시 돌리는 게 목적).
    # Robust + Gravity 옵션은 머지된 graph를 한 번 더 다듬는 데도 유효.
    command = [
        binary_path,
        "--Kp/DetectorStrategy=1",
        "--Vis/FeatureType=1",
        "--Mem/RehearsalSimilarity=0.6",
        "--Mem/NotLinkedNodesKept=true",
        "--Mem/InitWMWithAllNodes=true",
        "--Mem/STMSize=30",
        "--Rtabmap/LoopThr=0.05",
        "--Rtabmap/DetectionRate=0.0",
        "--Vis/MinInliers=12",
        "--RGBD/OptimizeMaxError=50.0",
        "--RGBD/ProximityBySpace=true",
        "--RGBD/ProximityMaxGraphDepth=0",
        "--Optimizer/Iterations=200",
        "--Optimizer/Strategy=2",
        "--Optimizer/Robust=false",
        "--Mem/UseOdomGravity=true",
        "--Optimizer/GravitySigma=0.3",
        "--uwarn",
        str(input_db),
        str(output_db),
    ]
    proc = await asyncio.create_subprocess_exec(
        *command,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        stdout_b, stderr_b = await asyncio.wait_for(proc.communicate(), timeout=timeout_s)
    except TimeoutError:
        proc.kill()
        await proc.wait()
        return {"status": "timeout", "timeout_s": timeout_s}
    stdout_text = stdout_b.decode(errors="replace") if stdout_b else ""
    if proc.returncode != 0:
        return {
            "status": "failed",
            "exit_code": proc.returncode,
            "stderr_tail": (stderr_b.decode(errors="replace") if stderr_b else "")[-1500:],
        }
    # 마지막 줄에서 loop closure 통계 추출
    closure_line = ""
    for line in reversed(stdout_text.splitlines()):
        if "Total loop closures" in line:
            closure_line = line.strip()
            break
    return {
        "status": "ok",
        "output_db_path": str(output_db),
        "loop_closure_summary": closure_line,
    }


def _align_submaps(merged_db: Path) -> dict[str, object]:
    """Stage 3 RANSAC SE(3) yaw-only alignment of sub-map B onto sub-map A."""
    try:
        align_func = load_alignment_dependency()
    except Exception as exc:
        return {"status": "skipped", "reason": f"import failed: {exc}"}
    try:
        result = align_func(merged_db)
    except Exception as exc:
        return {"status": "failed", "error": str(exc), "type": type(exc).__name__}
    return {
        "status": result.status,
        "cross_pair_count": result.cross_pair_count,
        "inlier_count": result.inlier_count,
        "rotation_deg": result.rotation_deg,
        "translation_xy": list(result.translation_xy),
        "patched_node_count": result.patched_node_count,
        "reason": result.reason,
    }


def load_alignment_dependency():
    backend_path = resolve_legacy_backend_src("align_submaps")
    prepend_sys_path(backend_path)
    from indoor_server.application.building.multiscan_alignment import (  # type: ignore
        align_and_patch_merged,
    )
    return align_and_patch_merged


def _merge_metadata_alongside(
    *,
    source_paths: list[Path],
    merged_rtabmap_db: Path,
    output_dir: Path,
) -> dict[str, object]:
    """Merge sidecar scan_metadata.db files into output_dir/scan_metadata.db.

    Each source rtabmap.db path's sibling scan_metadata.db is consumed.
    Sources without a sidecar are skipped; if none have one, no output is
    written and merge_status=='no_sources_with_metadata'.
    """
    merge_func, source_class = load_metadata_merge_dependencies()
    merge_sources = []
    skipped = []
    for src_rtabmap in source_paths:
        sidecar = src_rtabmap.parent / "scan_metadata.db"
        if not sidecar.exists():
            skipped.append(src_rtabmap.parent.name)
            continue
        merge_sources.append(source_class(
            scan_id=src_rtabmap.parent.name,
            metadata_db_path=sidecar,
            rtabmap_db_path=src_rtabmap,
        ))
    if not merge_sources:
        return {
            "merge_status": "no_sources_with_metadata",
            "skipped_sources": skipped,
        }
    output_db = output_dir / "scan_metadata.db"
    try:
        result = merge_func(
            sources=merge_sources,
            merged_rtabmap_db=merged_rtabmap_db,
            output_db=output_db,
        )
    except Exception as exc:
        return {
            "merge_status": "failed",
            "error": str(exc),
            "type": type(exc).__name__,
        }
    return {
        "merge_status": "ok",
        "output_db_path": str(result.output_db_path),
        "per_source_row_counts": result.per_source_row_counts,
        "total_rows_written": result.total_rows_written,
        "skipped_sources_without_metadata": skipped,
    }


def load_metadata_merge_dependencies():
    backend_path = resolve_legacy_backend_src("merge_scan_metadata")
    prepend_sys_path(backend_path)
    try:
        from indoor_server.application.building.multiscan_metadata_merge import (  # type: ignore
            MetadataMergeSource,
            merge_scan_metadata,
        )
    except Exception as exc:
        raise BridgeRuntimeError(
            "BRIDGE_BACKEND_IMPORT_FAILED",
            str(exc),
            {"type": type(exc).__name__, "module": "multiscan_metadata_merge"},
        ) from exc
    return merge_scan_metadata, MetadataMergeSource


def validate_build_superpoint_index(payload: dict[str, object]) -> None:
    require_string(payload, "scanId")
    require_string(payload, "dbPath")


def build_superpoint_index(payload: dict[str, object]) -> dict[str, object]:
    import time
    from pathlib import Path as _Path

    db_path = str(payload["dbPath"])
    scan_id = str(payload["scanId"])
    cache_dir_override = payload.get("cacheDir")
    if cache_dir_override:
        cache_dir = _Path(str(cache_dir_override))
    else:
        cache_dir = _Path(db_path).parent / "superpoint_index"

    backend_path = resolve_legacy_backend_src("build_superpoint_index")
    prepend_sys_path(backend_path)

    try:
        import torch  # noqa: F401  (ensures torch is importable before backend modules)
        from indoor_server.application.slam.superpoint.device import (  # type: ignore
            resolve_torch_device,
        )
        from indoor_server.application.slam.superpoint.map_manager import (  # type: ignore
            SuperPointLoadedMap,
        )
    except Exception as exc:
        raise BridgeRuntimeError(
            "BRIDGE_BACKEND_IMPORT_FAILED",
            str(exc),
            {"type": type(exc).__name__},
        ) from exc

    t0 = time.time()
    try:
        device = resolve_torch_device()
        loaded_map = SuperPointLoadedMap(scan_id, db_path, device, cache_dir=cache_dir)
    except Exception as exc:
        raise BridgeRuntimeError(
            "BRIDGE_SUPERPOINT_INDEX_FAILED",
            str(exc),
            {"scanId": scan_id, "dbPath": db_path, "type": type(exc).__name__},
        ) from exc

    elapsed_ms = int((time.time() - t0) * 1000)
    total_kp = sum(
        loaded_map.keyframe_feats[nid]["keypoints"].shape[1]
        for nid in loaded_map.node_ids
    )
    cache_bytes = sum(
        f.stat().st_size
        for f in cache_dir.iterdir()
        if f.is_file()
    ) if cache_dir.exists() else 0

    return {
        "cacheDir": str(cache_dir),
        "frameCount": len(loaded_map.node_ids),
        "totalKeypoints": total_kp,
        "bytes": cache_bytes,
        "elapsedMs": elapsed_ms,
    }


def validate_export_pointcloud(payload: dict[str, object]) -> None:
    require_string(payload, "scanId")
    require_string(payload, "dbPath")
    require_string(payload, "outputPath")


def export_pointcloud(payload: dict[str, object]) -> dict[str, object]:
    return asyncio.run(export_pointcloud_async(payload))


async def export_pointcloud_async(payload: dict[str, object]) -> dict[str, object]:
    import shutil
    import time

    db_path = Path(str(payload["dbPath"]))
    output_path = Path(str(payload["outputPath"]))
    if not db_path.exists():
        raise BridgeRuntimeError(
            "BRIDGE_EXPORT_DB_MISSING",
            f"rtabmap.db not found: {db_path}",
            {"dbPath": str(db_path)},
        )
    binary = os.environ.get("RTABMAP_EXPORT_BIN") or shutil.which("rtabmap-export")
    if not binary:
        raise BridgeRuntimeError(
            "BRIDGE_RTABMAP_EXPORT_UNAVAILABLE",
            "rtabmap-export binary not on PATH",
            {"hint": "install rtabmap-tools or set RTABMAP_EXPORT_BIN"},
        )

    output_path.parent.mkdir(parents=True, exist_ok=True)
    if output_path.exists():
        output_path.unlink()
    work_dir = output_path.parent / "_ply_export_work"
    if work_dir.exists():
        for leftover in work_dir.iterdir():
            try:
                leftover.unlink()
            except OSError:
                pass
    work_dir.mkdir(parents=True, exist_ok=True)

    t0 = time.time()
    # rtabmap-export writes <output_dir>/<prefix>_cloud.ply (or <prefix>.ply).
    command = [
        binary,
        "--output", "cloud",
        "--output_dir", str(work_dir),
        "--cloud_voxel", "0.02",
        str(db_path),
    ]
    proc = await asyncio.create_subprocess_exec(
        *command,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    timeout = float(payload.get("timeoutSeconds") or 600.0)
    try:
        _stdout_b, stderr_b = await asyncio.wait_for(proc.communicate(), timeout=timeout)
    except TimeoutError as exc:
        proc.kill()
        await proc.wait()
        raise BridgeRuntimeError(
            "BRIDGE_RTABMAP_EXPORT_TIMEOUT",
            "rtabmap-export timed out",
            {"timeoutSeconds": timeout},
        ) from exc
    if proc.returncode != 0:
        stderr_text = stderr_b.decode(errors="replace") if stderr_b else ""
        raise BridgeRuntimeError(
            "BRIDGE_RTABMAP_EXPORT_FAILED",
            "rtabmap-export exited with non-zero status",
            {"exitCode": proc.returncode, "stderrTail": stderr_text[-1500:]},
        )

    produced = _pick_exported_ply(work_dir)
    if produced is None:
        raise BridgeRuntimeError(
            "BRIDGE_RTABMAP_EXPORT_NO_OUTPUT",
            "rtabmap-export produced no .ply file",
            {"workDir": str(work_dir)},
        )
    produced.replace(output_path)
    _cleanup_work_dir(work_dir)

    return {
        "plyPath": str(output_path),
        "pointCount": _count_ply_vertices(output_path),
        "fileSize": output_path.stat().st_size,
        "elapsedMs": int((time.time() - t0) * 1000),
    }


def _pick_exported_ply(work_dir: Path) -> Path | None:
    for name in ("cloud.ply", "cloud_cloud.ply"):
        candidate = work_dir / name
        if candidate.exists():
            return candidate
    found = sorted(work_dir.glob("*.ply"))
    return found[0] if found else None


def _cleanup_work_dir(work_dir: Path) -> None:
    if not work_dir.exists():
        return
    for leftover in work_dir.iterdir():
        try:
            leftover.unlink()
        except OSError:
            pass
    try:
        work_dir.rmdir()
    except OSError:
        pass


def _count_ply_vertices(path: Path) -> int:
    try:
        with path.open("rb") as file:
            for _ in range(64):
                raw = file.readline()
                if not raw:
                    return 0
                line = raw.decode(errors="replace").strip()
                if line.startswith("element vertex"):
                    parts = line.split()
                    if len(parts) >= 3:
                        try:
                            return int(parts[2])
                        except ValueError:
                            return 0
                if line == "end_header":
                    return 0
    except OSError:
        return 0
    return 0


def load_legacy_merge_dependencies():
    backend_path = resolve_legacy_backend_src("merge_scan")
    prepend_sys_path(backend_path)
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


def resolve_legacy_backend_src(command: str) -> Path:
    backend_src = os.environ.get("INDOOR_LEGACY_BACKEND_SRC") or os.environ.get("PYTHON_BACKEND_SRC", "")
    backend_path = Path(backend_src).expanduser().resolve() if backend_src else default_legacy_backend_src()
    if not backend_path.exists():
        raise BridgeRuntimeError(
            "BRIDGE_BACKEND_NOT_CONFIGURED",
            "legacy Python backend source path does not exist",
            {
                "command": command,
                "path": str(backend_path),
                "defaultPath": str(default_legacy_backend_src()),
                "env": "INDOOR_LEGACY_BACKEND_SRC or PYTHON_BACKEND_SRC",
            },
        )
    return backend_path


def default_legacy_backend_src() -> Path:
    return Path(__file__).resolve().parents[2] / "python" / "legacy_backend" / "src"


def prepend_sys_path(path: Path) -> None:
    path_text = str(path)
    if path_text not in sys.path:
        sys.path.insert(0, path_text)


def requested_ml_device() -> str:
    return os.environ.get("INDOOR_ML_DEVICE", "cpu").strip().lower() or "cpu"


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
