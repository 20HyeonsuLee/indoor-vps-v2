"""Run RTAB-Map multi-database reprocess while preserving source provenance."""
from __future__ import annotations

import asyncio
import logging
import os
import re
import shutil
import sqlite3
import struct
import zlib
from dataclasses import dataclass, field
from pathlib import Path

from indoor_server.application.building.multiscan_pose_mapping import (
    SourceNodeRef,
    build_provenance_label,
)

logger = logging.getLogger(__name__)


class MultiScanRtabmapMergeError(Exception):
    """RTAB-Map multi-scan merge failed."""


def _rtabmap_param(name: str, value: object) -> list[str]:
    return [f"--{name}", str(value)]


def _rtabmap_bool(value: bool) -> str:
    return str(value).lower()


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
    # 1=SIFT, 0=SURF, 11=SuperPoint(Torch) — SuperPoint 는 WITH_TORCH 빌드 + .pt 모델 필요.
    # SuperPoint 가 cross-session/조명 변화에 매우 robust → 다른 시간대 스캔 머지에 핵심.
    feature_strategy: int = 1                     # SURF
    superpoint_enabled: bool = False
    superpoint_model_path: str = "/data/models/superpoint_v1.pt"
    superpoint_cuda: bool = True
    superpoint_threshold: float = 0.010
    superpoint_nms_radius: int = 4
    superpoint_nms: bool = True
    lightglue_enabled: bool = False
    lightglue_matcher_path: str = "/app/scripts/rtabmap_lightglue.py"
    lightglue_cuda: bool = True
    lightglue_threshold: float = 0.1
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
    use_odom_features: bool = True

    extra_args: tuple[str, ...] = ()

    def to_args(self) -> list[str]:
        args: list[str] = []
        if self.append_mode:
            args.append("-a")
        # SuperPoint 가 enabled 면 feature_strategy 를 11 로 override.
        # SuperPoint 옵션들은 아래에서 별도로 추가.
        active_strategy = 11 if self.superpoint_enabled else self.feature_strategy
        args.extend(
            [
                "-skip",
                str(self.skip),
                *_rtabmap_param("Kp/DetectorStrategy", active_strategy),
                *_rtabmap_param("Vis/FeatureType", active_strategy),
                *_rtabmap_param("Mem/RehearsalSimilarity", self.rehearsal_similarity),
                *_rtabmap_param("Mem/NotLinkedNodesKept", _rtabmap_bool(self.not_linked_nodes_kept)),
                *_rtabmap_param("Mem/ReduceGraph", _rtabmap_bool(self.reduce_graph)),
                *_rtabmap_param("Mem/InitWMWithAllNodes", _rtabmap_bool(self.init_wm_with_all_nodes)),
                *_rtabmap_param("Mem/STMSize", self.stm_size),
                *_rtabmap_param("Rtabmap/MemoryThr", self.memory_thr),
                *_rtabmap_param("Rtabmap/TimeThr", self.time_thr),
                *_rtabmap_param("Rtabmap/LoopThr", self.loop_thr),
                *_rtabmap_param("Rtabmap/DetectionRate", self.detection_rate),
                *_rtabmap_param("Vis/MinInliers", self.vis_min_inliers),
                *_rtabmap_param("Vis/BundleAdjustment", 0),
                *_rtabmap_param("RGBD/OptimizeMaxError", self.optimize_max_error),
                *_rtabmap_param("RGBD/ProximityBySpace", _rtabmap_bool(self.proximity_by_space)),
                *_rtabmap_param("RGBD/ProximityMaxGraphDepth", self.proximity_max_graph_depth),
                *_rtabmap_param("Optimizer/Iterations", self.optimizer_iterations),
                *_rtabmap_param("Optimizer/Strategy", self.optimizer_strategy),
                *_rtabmap_param("Mem/IncrementalMemory", _rtabmap_bool(self.incremental_memory)),
                *_rtabmap_param("Optimizer/Robust", _rtabmap_bool(self.optimizer_robust)),
                *_rtabmap_param("Mem/UseOdomGravity", _rtabmap_bool(self.use_odom_gravity)),
                *_rtabmap_param("Optimizer/GravitySigma", self.gravity_sigma),
            ]
        )
        active_use_odom_features = False if self.superpoint_enabled else self.use_odom_features
        args.extend([
            *_rtabmap_param("Mem/UseOdomFeatures", _rtabmap_bool(active_use_odom_features)),
        ])
        if self.superpoint_enabled:
            args.extend([
                *_rtabmap_param("SuperPoint/ModelPath", self.superpoint_model_path),
                *_rtabmap_param("SuperPoint/Cuda", _rtabmap_bool(self.superpoint_cuda)),
                *_rtabmap_param("SuperPoint/Threshold", self.superpoint_threshold),
                *_rtabmap_param("SuperPoint/NMSRadius", self.superpoint_nms_radius),
                *_rtabmap_param("SuperPoint/NMS", _rtabmap_bool(self.superpoint_nms)),
                *_rtabmap_param("RGBD/LoopClosureReextractFeatures", "true"),
            ])
        if self.lightglue_enabled:
            args.extend([
                *_rtabmap_param("Vis/CorNNType", 6),
                *_rtabmap_param("PyMatcher/Path", self.lightglue_matcher_path),
                *_rtabmap_param("PyMatcher/Cuda", _rtabmap_bool(self.lightglue_cuda)),
                *_rtabmap_param("PyMatcher/Threshold", self.lightglue_threshold),
            ])
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


@dataclass(frozen=True)
class RefinementPassParams:
    """Aggressive cross-session detectMoreLoopClosures pass parameters.

    Chain merge (rtabmap-reprocess -a) 만으로는 inter-session LC 가 인접 세션끼리만
    잡혀 chain 끝단 drift 가 누적됨. 이 pass 는 cross-session LC 를 적극 탐색해
    chain topology 를 star/loop 에 가깝게 보강한다.
    """
    radius_m: float = 3.0           # 인접 노드 검색 반경 (기본 1m → 확장)
    angle_deg: float = 45.0          # yaw 허용 각도 (기본 30° → 확장)
    iterations: int = 1              # detectMoreLoopClosures 내부 -i (3 → 1, 시간 단축)
    optimize_max_error: float = 50.0 # cross-session 큰 transform LC reject 방지 (기본 2)
    optimizer_strategy: int = 2      # GTSAM
    vis_min_inliers: int = 10
    intra_session_merge: bool = True # --intra: 같은 session 안 LC 도 추가 (안전, 정합 강화)
    timeout_s: float = 1800.0        # 30분 — radius 큰 pass (5m+) 가 50000+ pair 처리 가능


@dataclass(frozen=True)
class MergeRefinementConfig:
    """Multi-pass refinement loop 설정.

    pair 수가 O(N × radius²) 로 폭발 → 큰 머지에서 시간 폭발.
    Default 는 보수적으로 pass 2 + 작은 radius (3m → 5m). 더 적극적인 cross-session
    LC 필요하면 max_passes/radius_growth_m 늘리거나 pass 별로 별도 호출.
    """
    enabled: bool = True
    max_passes: int = 2                # 3 → 2 (시간 단축, pair 폭발 방지)
    convergence_min_new_lc: int = 5
    convergence_max_centroid_dist_m: float = 60.0
    radius_growth_m: float = 2.0       # pass 0: 3m, pass 1: 5m
    angle_growth_deg: float = 15.0


class MultiScanRtabmapReprocessRunner:
    """`rtabmap-reprocess -a "db1;db2"` wrapper for evidence and later production use."""

    def __init__(
        self,
        *,
        binary_path: str | None = None,
        detect_more_lc_binary: str | None = None,
        default_timeout_s: float = 900.0,
    ) -> None:
        self._binary_path = binary_path or os.environ.get(
            "RTABMAP_REPROCESS_BIN"
        ) or shutil.which("rtabmap-reprocess")
        self._detect_more_lc_bin = (
            detect_more_lc_binary
            or os.environ.get("RTABMAP_DETECT_MORE_LC_BIN")
            or shutil.which("rtabmap-detectMoreLoopClosures")
        )
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
        refinement: MergeRefinementConfig | None = None,
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

        # Phase 1 (chain merge) 완료 직후 통계
        chain_loop_count = count_loop_closures(output_db)
        chain_drift = self._analyze_subgraph_drift(output_db)

        # Phase 2+: aggressive cross-session loop closure detection (multi-pass)
        refinement_cfg = refinement if refinement is not None else MergeRefinementConfig()
        refinement_passes: list[dict] = []
        if refinement_cfg.enabled:
            try:
                refinement_passes = await self._refine_cross_session(
                    output_db, refinement_cfg
                )
            except Exception as exc:
                # refinement 는 fail-soft — Phase 1 결과는 유지
                logger.warning("refinement passes failed: %s", exc)
                refinement_passes = [{"error": str(exc)}]

        final_drift = self._analyze_subgraph_drift(output_db)

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
            extra={
                "chain_loop_count": chain_loop_count,
                "chain_drift": chain_drift,
                "refinement_enabled": refinement_cfg.enabled,
                "refinement_passes": refinement_passes,
                "final_drift": final_drift,
            },
        )
        logger.info(
            "rtabmap multi-scan reprocess complete (with refinement)",
            extra=result.to_metadata(),
        )
        return result

    # ------------------------------------------------------------------
    # Phase 2: aggressive cross-session loop closure detection
    # ------------------------------------------------------------------

    async def _refine_cross_session(
        self,
        db_path: Path,
        config: MergeRefinementConfig,
    ) -> list[dict]:
        """Multi-pass aggressive detectMoreLoopClosures with convergence check.

        Chain merge 후 inter-session LC 가 인접 세션끼리만 잡힌 상태에서
        cross-session LC 를 적극 탐색해 chain topology → star/loop 로 보강.
        매 pass 마다 search radius / yaw 점진 확장. 새 inter-session LC 가
        거의 없거나 sub-map centroid 거리가 임계 이내로 수렴하면 stop.
        """
        if not self._detect_more_lc_bin:
            return [{"skipped": "rtabmap-detectMoreLoopClosures binary not found"}]

        passes: list[dict] = []
        prev_inter_lc = self._count_inter_session_lc(db_path)

        for it in range(config.max_passes):
            params = RefinementPassParams(
                radius_m=3.0 + it * config.radius_growth_m,
                angle_deg=45.0 + it * config.angle_growth_deg,
                iterations=3,
                optimize_max_error=50.0,
                optimizer_strategy=2,
                vis_min_inliers=max(8, 12 - it),  # 점진 lax (수렴 어려우면 lower inliers)
            )
            pass_result = await self._run_detect_more_loop_closures(db_path, params)
            new_inter_lc = self._count_inter_session_lc(db_path)
            drift = self._analyze_subgraph_drift(db_path)
            added = new_inter_lc - prev_inter_lc
            passes.append({
                "iteration": it,
                "params": {
                    "radius_m": params.radius_m,
                    "angle_deg": params.angle_deg,
                    "iterations": params.iterations,
                    "vis_min_inliers": params.vis_min_inliers,
                },
                "detected_lc_in_run": pass_result.get("detected_lc"),
                "inter_session_lc_before": prev_inter_lc,
                "inter_session_lc_after": new_inter_lc,
                "added_inter_lc": added,
                "max_centroid_dist_m": drift.get("max_centroid_dist_m"),
                "session_count": drift.get("session_count"),
                "duration_s": pass_result.get("duration_s"),
            })
            prev_inter_lc = new_inter_lc

            # 수렴 판정: 새 inter-session LC 부족 + sub-map 거리 임계 이내
            max_dist = drift.get("max_centroid_dist_m", float("inf"))
            if (added < config.convergence_min_new_lc
                    and max_dist < config.convergence_max_centroid_dist_m):
                passes[-1]["converged"] = True
                break

        return passes

    async def _run_detect_more_loop_closures(
        self,
        db_path: Path,
        params: RefinementPassParams,
    ) -> dict:
        command = [
            self._detect_more_lc_bin,
            "-r", str(params.radius_m),
            "-a", str(params.angle_deg),
            "-i", str(params.iterations),
        ]
        if params.intra_session_merge:
            command.append("--intra")
        command.extend([
            "--RGBD/OptimizeMaxError", str(params.optimize_max_error),
            "--Optimizer/Strategy", str(params.optimizer_strategy),
            "--Optimizer/GravitySigma", "0.3",
            "--Mem/UseOdomGravity", "true",
            "--Mem/InitWMWithAllNodes", "true",
            "--Vis/EstimationType", "2",
            "--Vis/MinInliers", str(params.vis_min_inliers),
            str(db_path),
        ])

        loop = asyncio.get_running_loop()
        start = loop.time()
        proc = await asyncio.create_subprocess_exec(
            *command,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        try:
            stdout_b, stderr_b = await asyncio.wait_for(
                proc.communicate(), timeout=params.timeout_s
            )
        except TimeoutError as e:
            proc.kill()
            await proc.wait()
            raise MultiScanRtabmapMergeError(
                f"rtabmap-detectMoreLoopClosures timeout > {params.timeout_s}s"
            ) from e
        duration = loop.time() - start

        stdout_text = stdout_b.decode(errors="replace") if stdout_b else ""
        # "Detected N total loop closures!" 추출
        detected = None
        m = re.search(r"Detected\s+(\d+)\s+total loop closures", stdout_text)
        if m:
            detected = int(m.group(1))

        return {
            "command": command,
            "duration_s": duration,
            "exit_code": proc.returncode,
            "detected_lc": detected,
            "stdout_tail": stdout_text[-800:],
        }

    # ------------------------------------------------------------------
    # Diagnostics: session boundary 추출 + inter-session LC 수 + centroid 거리
    # ------------------------------------------------------------------

    def _session_id_ranges(self, conn: sqlite3.Connection) -> list[tuple[str, int, int]]:
        """Return [(session_label, start_id, end_id), ...].

        rtabmap-reprocess -a 가 각 입력 db 의 첫 노드에 'map<i>' label 을 박음.
        그 marker 들의 id 사이 가 한 session 의 노드 범위.
        """
        markers = conn.execute(
            "SELECT id, label FROM Node WHERE label LIKE 'map%' ORDER BY id"
        ).fetchall()
        if not markers:
            return []
        max_id_row = conn.execute("SELECT MAX(id) FROM Node").fetchone()
        if not max_id_row or max_id_row[0] is None:
            return []
        max_id = int(max_id_row[0])
        ranges = []
        for i, (start_id, lbl) in enumerate(markers):
            end_id = markers[i + 1][0] - 1 if i + 1 < len(markers) else max_id
            ranges.append((str(lbl), int(start_id), int(end_id)))
        return ranges

    def _count_inter_session_lc(self, db_path: Path) -> int:
        """Type 1/2/3 LC links 중 두 끝 노드가 다른 session 인 것 카운트."""
        try:
            conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
        except sqlite3.Error:
            return 0
        try:
            ranges = self._session_id_ranges(conn)
            if not ranges:
                return 0
            inter = 0
            for f, t, _ty in conn.execute(
                "SELECT from_id, to_id, type FROM Link WHERE type IN (1,2,3)"
            ):
                sf = self._session_for_id(int(f), ranges)
                st = self._session_for_id(int(t), ranges)
                if sf and st and sf != st:
                    inter += 1
            return inter
        finally:
            conn.close()

    @staticmethod
    def _session_for_id(node_id: int, ranges: list[tuple[str, int, int]]) -> str | None:
        for lbl, s, e in ranges:
            if s <= node_id <= e:
                return lbl
        return None

    def _analyze_subgraph_drift(self, db_path: Path) -> dict:
        """Admin.opt_poses 의 session 별 centroid + 가장 먼 두 centroid 거리."""
        try:
            conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
        except sqlite3.Error as exc:
            return {"error": f"db open failed: {exc}"}
        try:
            row = conn.execute(
                "SELECT opt_ids, opt_poses FROM Admin "
                "WHERE opt_ids IS NOT NULL AND opt_poses IS NOT NULL "
                "ORDER BY rowid DESC LIMIT 1"
            ).fetchone()
            if not row or not row[0] or not row[1]:
                return {"error": "no opt_poses"}
            ids_data = zlib.decompress(row[0])
            poses_data = zlib.decompress(row[1])
            n = len(ids_data) // 4
            if n == 0 or len(poses_data) // 48 != n:
                return {"error": "opt_poses size mismatch"}
            opt_ids = list(struct.unpack("<" + "i" * n, ids_data))
            ranges = self._session_id_ranges(conn)
            if not ranges:
                return {"error": "no session markers (map<i> labels)"}

            sessions: dict[str, list[tuple[float, float, float]]] = {}
            for i, nid in enumerate(opt_ids):
                p = struct.unpack("<12f", poses_data[i * 48:(i + 1) * 48])
                if all(v == 0.0 for v in p):
                    continue
                lbl = self._session_for_id(int(nid), ranges)
                if not lbl:
                    continue
                sessions.setdefault(lbl, []).append((p[3], p[7], p[11]))

            centroids: dict[str, tuple[float, float, float]] = {}
            for lbl, pts in sessions.items():
                if not pts:
                    continue
                cx = sum(p[0] for p in pts) / len(pts)
                cy = sum(p[1] for p in pts) / len(pts)
                cz = sum(p[2] for p in pts) / len(pts)
                centroids[lbl] = (cx, cy, cz)

            labels = sorted(centroids.keys())
            max_dist = 0.0
            for i in range(len(labels)):
                for j in range(i + 1, len(labels)):
                    c1 = centroids[labels[i]]
                    c2 = centroids[labels[j]]
                    d = (
                        (c1[0] - c2[0]) ** 2
                        + (c1[1] - c2[1]) ** 2
                        + (c1[2] - c2[2]) ** 2
                    ) ** 0.5
                    if d > max_dist:
                        max_dist = d
            return {
                "session_count": len(centroids),
                "centroids": {k: list(v) for k, v in centroids.items()},
                "max_centroid_dist_m": max_dist,
                "node_count_per_session": {k: len(v) for k, v in sessions.items()},
            }
        finally:
            conn.close()


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
