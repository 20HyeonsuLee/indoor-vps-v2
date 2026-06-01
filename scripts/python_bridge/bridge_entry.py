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


COMMANDS = ("health", "localize", "merge_scan", "build_superpoint_index", "export_pointcloud")


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
        print(json.dumps({
            "ok": True,
            "commands": list(COMMANDS),
            "mlDevice": requested_ml_device(),
            "cudaVisibleDevices": os.environ.get("CUDA_VISIBLE_DEVICES", "<unset>"),
        }))
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
    if command == "build_superpoint_index":
        validate_build_superpoint_index(payload)
        if payload.get("contractOnly") is True:
            print_json({"ok": True, "command": command})
            return 0
        return build_superpoint_index(payload)
    if command == "export_pointcloud":
        validate_export_pointcloud(payload)
        if payload.get("contractOnly") is True:
            print_json({"ok": True, "command": command})
            return 0
        print_json(export_pointcloud(payload))
        return 0
    return fail("BRIDGE_UNKNOWN_COMMAND", f"unknown bridge command: {command}", {"commands": list(COMMANDS)})


def validate_localize(payload: dict[str, object]) -> None:
    require_string(payload, "buildingId")
    require_string(payload, "storageRoot")
    require_string_list(payload, "imagePaths", min_items=1)
    require_floor_maps(payload)


def validate_merge_scan(payload: dict[str, object]) -> None:
    # floorId/scanId 는 머지(reprocess)에 쓰이지 않는 메타 — optional. merge_scan_async 는
    # sourcePaths + outputDir 만 사용한다. (merge-build 워커 경로는 floorId 를 보내지 않음)
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
    # source 별 provenance id. scans/<scanId>/rtabmap.db 레이아웃은 부모 dir(=scanId),
    # 임의 경로(예: Downloads/260522-234741.db)는 파일 stem 을 쓴다 — 부모 dir 만 쓰면
    # 같은 폴더의 여러 db 가 동일 id 로 충돌한다.
    def _source_scan_id(p: Path) -> str:
        return p.parent.name if p.name == "rtabmap.db" else p.stem

    sources = [
        source_class(scan_id=_source_scan_id(Path(path)), db_path=Path(path))
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


def validate_build_superpoint_index(payload: dict[str, object]) -> None:
    require_string(payload, "scanId")
    require_string(payload, "dbPath")


def build_superpoint_index(payload: dict[str, object]) -> int:
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
        import torch
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

    print_json({
        "cacheDir": str(cache_dir),
        "frameCount": len(loaded_map.node_ids),
        "totalKeypoints": total_kp,
        "bytes": cache_bytes,
        "elapsedMs": elapsed_ms,
    })
    return 0


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
        # --cloud: ARKit/RGB-D depth는 Data.scan이 아닌 Data.depth에 들어가므로 cloud 모드.
        "--cloud",
        # opt=2: reprocess + detectMoreLoopClosures가 Admin.opt_poses에 저장한
        # graph-optimized pose 활용 (default 0은 재최적화 시도. 우리는 이미
        # optimization 완료된 결과 사용해 일관성 유지).
        "--opt", "2",
        # decimation 1 = depth image 전체 픽셀 사용 (default 4는 1/4 down → 디테일 손실).
        "--decimation", "1",
        # 10cm voxel. 시각화 부담 줄이는 우선.
        "--voxel", "0.10",
        # ARKit sceneDepth 신뢰 범위 5m (≥6m는 노이즈 폭증).
        "--max_range", "5",
        # Isolated outlier 제거. noise_k=5는 rtabmap default. ARKit raw depth는
        # view-간 일관성이 약해 noise_k=2(이전값)에선 ray-방향 줄무늬 outlier가
        # 그대로 남았음 → 5cm 반경에 이웃 5개 미만이면 컷으로 강화 (point ~76% 컷).
        "--noise_radius", "0.05",
        "--noise_k", "5",
        # ARKit confidenceMap (Low=0/Med=50/High=100) 임계치. 클라가 depth_confidence를
        # 보내기 시작하면 신뢰도 50 미만 픽셀이 자동 컷되어 노이즈가 더 깎임.
        # depth_confidence 칼럼이 NULL인 기존 scan은 이 옵션이 무시되므로 BC 유지.
        "--depth_confidence", "50",
        "--output", "cloud",
        "--output_dir", str(work_dir),
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
