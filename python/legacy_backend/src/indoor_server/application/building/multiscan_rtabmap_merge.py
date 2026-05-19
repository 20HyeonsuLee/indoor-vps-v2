"""Run RTAB-Map multi-database reprocess while preserving source provenance."""
from __future__ import annotations

import asyncio
import logging
import os
import shutil
import sqlite3
from dataclasses import dataclass, field
from pathlib import Path

from indoor_server.application.building.multiscan_pose_mapping import (
    SourceNodeRef,
    build_provenance_label,
)

logger = logging.getLogger(__name__)


class MultiScanRtabmapMergeError(Exception):
    """RTAB-Map multi-scan merge failed."""


@dataclass(frozen=True)
class SourceRtabmapScan:
    scan_id: str
    db_path: Path


@dataclass(frozen=True)
class PreparedRtabmapScan:
    scan_id: str
    source_db_path: Path
    prepared_db_path: Path
    labeled_node_count: int


@dataclass(frozen=True)
class MultiScanReprocessParams:
    # Multi-DB merge가 cross-session loop closure에 의존해 sub-map 좌표계를
    # 정렬하므로, 기본값은 loop closure를 적극적으로 탐색하는 방향으로 잡음.
    append_mode: bool = True
    skip: int = 0
    feature_strategy: int = 1                     # SURF
    rehearsal_similarity: float = 0.6             # 기본값으로 복원 (1.0 → 0.6)
    not_linked_nodes_kept: bool = True
    reduce_graph: bool = False
    memory_thr: int = 0
    time_thr: int = 0
    # 두 ARKit world의 origin 차이가 클 때(예: 30~40m) graph optimizer가
    # cross-session loop closure를 outlier로 reject해 정렬이 망가짐. 50m로
    # 완화해야 두 sub-map 간 큰 transform이 허용되어 정렬 성공.
    # (이전 프로덕션 시스템에서 검증된 값.)
    optimize_max_error: float = 50.0
    warn: bool = True

    # multi-session loop closure 핵심: 두 번째 DB 로드 시 첫 DB의 모든 노드를
    # working memory에 두어야 cross-session 매칭이 가능.
    init_wm_with_all_nodes: bool = True
    # loop closure 탐색을 적극적으로 (낮을수록 적극)
    loop_thr: float = 0.05                        # 0.11 → 0.05
    vis_min_inliers: int = 12                     # 20 → 12
    detection_rate: float = 0.0                   # 1Hz → 모든 노드에서 시도
    stm_size: int = 30                            # 10 → 30
    proximity_max_graph_depth: int = 0            # 50 → 0 (cross-session proximity 풀기)
    proximity_by_space: bool = True
    optimizer_iterations: int = 200               # 100 → 200

    # 정렬 결정타: -a + IncrementalMemory=false → 두 번째 DB가 첫 DB에
    # localization-only로 처리되어 sub-map B가 sub-map A 좌표계로 강제 정렬.
    # 이 조합 없이는 두 sub-map 모두 자기 origin (0,0,0)에서 시작해 따로 살아남음.
    incremental_memory: bool = False
    # Vertigo robust optimization (g2o/GTSAM 필요). cross-map closure가 풍부할 때
    # robust kernel이 boundary constraint를 outlier로 down-weight해버려 sub-map 정렬
    # 자체가 안 되는 케이스 확인됨 → 기본 비활성. 노이즈가 많은 환경에서만 켜기.
    optimizer_robust: bool = False
    # GTSAM (2) 또는 g2o (1). multi-session에 GTSAM이 더 잘 작동한다는 보고.
    optimizer_strategy: int = 2
    # ARKit pose는 gravity-aligned이므로 중력 제약을 active. multi-session 정렬에
    # 강한 사전 정보 제공.
    use_odom_gravity: bool = True
    gravity_sigma: float = 0.3

    extra_args: tuple[str, ...] = ()

    def to_args(self) -> list[str]:
        args: list[str] = []
        if self.append_mode:
            args.append("-a")
        args.extend(
            [
                "-skip",
                str(self.skip),
                f"--Kp/DetectorStrategy={self.feature_strategy}",
                f"--Vis/FeatureType={self.feature_strategy}",
                f"--Mem/RehearsalSimilarity={self.rehearsal_similarity}",
                f"--Mem/NotLinkedNodesKept={str(self.not_linked_nodes_kept).lower()}",
                f"--Mem/ReduceGraph={str(self.reduce_graph).lower()}",
                f"--Mem/InitWMWithAllNodes={str(self.init_wm_with_all_nodes).lower()}",
                f"--Mem/STMSize={self.stm_size}",
                f"--Rtabmap/MemoryThr={self.memory_thr}",
                f"--Rtabmap/TimeThr={self.time_thr}",
                f"--Rtabmap/LoopThr={self.loop_thr}",
                f"--Rtabmap/DetectionRate={self.detection_rate}",
                f"--Vis/MinInliers={self.vis_min_inliers}",
                f"--RGBD/OptimizeMaxError={self.optimize_max_error}",
                f"--RGBD/ProximityBySpace={str(self.proximity_by_space).lower()}",
                f"--RGBD/ProximityMaxGraphDepth={self.proximity_max_graph_depth}",
                f"--Optimizer/Iterations={self.optimizer_iterations}",
                f"--Optimizer/Strategy={self.optimizer_strategy}",
                f"--Mem/IncrementalMemory={str(self.incremental_memory).lower()}",
                f"--Optimizer/Robust={str(self.optimizer_robust).lower()}",
                f"--Mem/UseOdomGravity={str(self.use_odom_gravity).lower()}",
                f"--Optimizer/GravitySigma={self.gravity_sigma}",
            ]
        )
        if self.warn:
            args.append("--uwarn")
        args.extend(self.extra_args)
        return args


@dataclass(frozen=True)
class MultiScanReprocessResult:
    output_db_path: Path
    duration_s: float
    source_scan_ids: list[str]
    prepared_scans: list[PreparedRtabmapScan]
    command: list[str]
    stdout_text: str
    stderr_text: str
    merged_node_count: int
    loop_closure_count: int
    extra: dict[str, object] = field(default_factory=dict)

    def to_metadata(self) -> dict[str, object]:
        return {
            "output_db_path": str(self.output_db_path),
            "duration_s": self.duration_s,
            "source_scan_ids": self.source_scan_ids,
            "prepared_scans": [
                {
                    "scan_id": scan.scan_id,
                    "source_db_path": str(scan.source_db_path),
                    "prepared_db_path": str(scan.prepared_db_path),
                    "labeled_node_count": scan.labeled_node_count,
                }
                for scan in self.prepared_scans
            ],
            "command": self.command,
            "merged_node_count": self.merged_node_count,
            "loop_closure_count": self.loop_closure_count,
            "extra": self.extra,
        }


class MultiScanRtabmapReprocessRunner:
    """`rtabmap-reprocess -a "db1;db2"` wrapper for evidence and later production use."""

    def __init__(
        self,
        *,
        binary_path: str | None = None,
        default_timeout_s: float = 900.0,
    ) -> None:
        self._binary_path = binary_path or os.environ.get(
            "RTABMAP_REPROCESS_BIN"
        ) or shutil.which("rtabmap-reprocess")
        self._default_timeout_s = default_timeout_s

    @property
    def binary_path(self) -> str | None:
        return self._binary_path

    def is_available(self) -> bool:
        return self._binary_path is not None and (
            Path(self._binary_path).exists() or shutil.which(self._binary_path) is not None
        )

    async def run(
        self,
        *,
        sources: list[SourceRtabmapScan],
        output_db: Path,
        work_dir: Path,
        params: MultiScanReprocessParams | None = None,
        timeout_s: float | None = None,
    ) -> MultiScanReprocessResult:
        if self._binary_path is None:
            raise MultiScanRtabmapMergeError(
                "rtabmap-reprocess binary is not available in PATH or RTABMAP_REPROCESS_BIN."
            )
        if len(sources) < 2:
            raise MultiScanRtabmapMergeError("multi-scan merge requires at least 2 sources")

        for source in sources:
            if not source.db_path.exists():
                raise MultiScanRtabmapMergeError(f"source db missing: {source.db_path}")

        output_db.parent.mkdir(parents=True, exist_ok=True)
        work_dir.mkdir(parents=True, exist_ok=True)
        if output_db.exists():
            output_db.unlink()

        prepared = prepare_rtabmap_sources(sources=sources, work_dir=work_dir)
        input_arg = ";".join(str(scan.prepared_db_path) for scan in prepared)
        effective_params = params or MultiScanReprocessParams()
        command = [
            self._binary_path,
            *effective_params.to_args(),
            input_arg,
            str(output_db),
        ]
        timeout = timeout_s if timeout_s is not None else self._default_timeout_s

        loop = asyncio.get_running_loop()
        start = loop.time()
        proc = await asyncio.create_subprocess_exec(
            *command,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        try:
            stdout_b, stderr_b = await asyncio.wait_for(
                proc.communicate(), timeout=timeout
            )
        except TimeoutError as e:
            proc.kill()
            await proc.wait()
            raise MultiScanRtabmapMergeError(
                f"rtabmap multi-scan reprocess timeout > {timeout}s"
            ) from e
        duration = loop.time() - start

        stdout_text = stdout_b.decode(errors="replace") if stdout_b else ""
        stderr_text = stderr_b.decode(errors="replace") if stderr_b else ""
        if proc.returncode != 0:
            raise MultiScanRtabmapMergeError(
                f"rtabmap multi-scan reprocess exit code {proc.returncode}\n"
                f"stderr tail: {stderr_text[-1200:]}"
            )

        result = MultiScanReprocessResult(
            output_db_path=output_db,
            duration_s=duration,
            source_scan_ids=[source.scan_id for source in sources],
            prepared_scans=prepared,
            command=command,
            stdout_text=stdout_text,
            stderr_text=stderr_text,
            merged_node_count=count_nodes(output_db),
            loop_closure_count=count_loop_closures(output_db),
        )
        logger.info(
            "rtabmap multi-scan reprocess complete",
            extra=result.to_metadata(),
        )
        return result


def prepare_rtabmap_sources(
    *,
    sources: list[SourceRtabmapScan],
    work_dir: Path,
) -> list[PreparedRtabmapScan]:
    work_dir.mkdir(parents=True, exist_ok=True)
    seen: set[str] = set()
    prepared: list[PreparedRtabmapScan] = []
    for index, source in enumerate(sources):
        if source.scan_id in seen:
            raise MultiScanRtabmapMergeError(f"duplicate source scan_id: {source.scan_id}")
        seen.add(source.scan_id)
        safe_scan_id = "".join(ch if ch.isalnum() or ch in "-_" else "_" for ch in source.scan_id)
        target = work_dir / f"{index:02d}_{safe_scan_id}.db"
        if target.exists():
            target.unlink()
        shutil.copy2(source.db_path, target)
        _verify_sqlite_integrity(target, scan_id=source.scan_id)
        labeled = inject_provenance_labels(target, scan_id=source.scan_id)
        prepared.append(
            PreparedRtabmapScan(
                scan_id=source.scan_id,
                source_db_path=source.db_path,
                prepared_db_path=target,
                labeled_node_count=labeled,
            )
        )
    return prepared


def _verify_sqlite_integrity(db_path: Path, *, scan_id: str) -> None:
    """copy 직후 PRAGMA integrity_check — race(소스 reprocess가 아직 쓰는 중인데
    복사가 그 순간을 잡으면 SQLite 헤더는 있지만 page truncated → 머지가
    "database disk image is malformed" 로 폭주). 명확한 에러로 fail-fast.
    """
    try:
        conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    except sqlite3.Error as exc:
        raise MultiScanRtabmapMergeError(
            f"source DB unopenable (scan_id={scan_id}, path={db_path}): {exc}"
        ) from exc
    try:
        row = conn.execute("PRAGMA integrity_check").fetchone()
    except sqlite3.DatabaseError as exc:
        raise MultiScanRtabmapMergeError(
            f"source DB malformed (scan_id={scan_id}): {exc}. "
            f"청크의 reprocess가 끝나기 전에 머지가 시작됐을 가능성 — "
            f"build_state=succeeded 확인 후 재시도."
        ) from exc
    finally:
        conn.close()
    status = (row[0] if row else "") or ""
    if status.lower() != "ok":
        raise MultiScanRtabmapMergeError(
            f"source DB integrity_check failed (scan_id={scan_id}): {status}"
        )


def inject_provenance_labels(db_path: Path, *, scan_id: str) -> int:
    """Stamp every Node with provenance — both into Node.label (primary) and
    Data.user_data (fallback). rtabmap-reprocess `-a` overwrites Node.label for
    the appended DB (map_id=1+), but preserves Data.user_data BLOBs, so the
    fallback lets us recover provenance for *every* merged node.
    """
    conn = sqlite3.connect(str(db_path))
    conn.row_factory = sqlite3.Row
    try:
        rows = conn.execute(
            "SELECT id, stamp, label FROM Node ORDER BY id"
        ).fetchall()
        for row in rows:
            source = SourceNodeRef(
                scan_id=scan_id,
                node_id=int(row["id"]),
                stamp=float(row["stamp"] or 0.0),
                original_label=row["label"],
            )
            provenance = build_provenance_label(source)
            conn.execute(
                "UPDATE Node SET label = ? WHERE id = ?",
                (provenance, int(row["id"])),
            )
            # user_data BLOB: prefix with magic header so we can extract later
            # without colliding with caller-supplied user_data payloads.
            user_blob = (USER_DATA_PROVENANCE_PREFIX + provenance).encode("utf-8")
            conn.execute(
                "UPDATE Data SET user_data = ? WHERE id = ?",
                (user_blob, int(row["id"])),
            )
        conn.commit()
        return len(rows)
    finally:
        conn.close()


USER_DATA_PROVENANCE_PREFIX = "__ipf_src_v1__\x00"


def count_nodes(db_path: Path) -> int:
    return _count_scalar(db_path, "SELECT COUNT(*) FROM Node")


def count_loop_closures(db_path: Path) -> int:
    return _count_scalar(db_path, "SELECT COUNT(*) FROM Link WHERE type IN (1, 2)")


def _count_scalar(db_path: Path, query: str) -> int:
    if not db_path.exists():
        return 0
    conn = sqlite3.connect(f"file:{db_path}?mode=ro&immutable=1", uri=True)
    try:
        row = conn.execute(query).fetchone()
        return int(row[0]) if row else 0
    except sqlite3.Error:
        return 0
    finally:
        conn.close()
