"""Merge per-source scan_metadata.db files into a single sidecar aligned with
the merged rtabmap.db.

Each iOS scan has its own ARKit world origin, so naive concat would place
marks from different sources on top of each other. We use the per-source
node-pose mappings produced by `multiscan_pose_mapping.build_node_pose_mapping`
to derive a 4x4 correction transform (source-rtabmap-frame → merged-rtabmap-frame)
for each source rtabmap Node, then apply it to the (tx, ty, tz) of every
keyframe / mark.

ScanMetadataIntegrator (Java) reads scan_metadata.db expecting ARKit world
coords and converts via ArKitToRtabmap (rt_x=-arkit_z, rt_y=-arkit_x,
rt_z=arkit_y). We keep that contract by encoding the corrected position back
into ARKit-style coords before writing.

Integer primary keys (branch_mark.id, branch_edge.id, poi_mark.id,
poi_photo.id, interfloor_mark.id) are reassigned globally to avoid PK
collisions across sources. branch_edge.from_node_id/to_node_id and
branch_mark.connect_node_id (both TEXT references to branch_mark.id) are
remapped accordingly.
"""
from __future__ import annotations

import logging
import sqlite3
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import numpy as np

from indoor_server.application.building.multiscan_pose_mapping import (
    NodePoseMapping,
    NodePoseMappingResult,
    build_node_pose_mapping,
)

logger = logging.getLogger(__name__)

# 4x4 basis change ARKit → rtabmap-cam-body (matches Java ArKitToRtabmap):
#   rt_x = -arkit_z, rt_y = -arkit_x, rt_z = arkit_y
_ARKIT_TO_RT = np.array(
    [
        [0.0, 0.0, -1.0, 0.0],
        [-1.0, 0.0, 0.0, 0.0],
        [0.0, 1.0, 0.0, 0.0],
        [0.0, 0.0, 0.0, 1.0],
    ],
    dtype=np.float64,
)
# inverse: arkit_x = -rt_y, arkit_y = rt_z, arkit_z = -rt_x
_RT_TO_ARKIT = np.array(
    [
        [0.0, -1.0, 0.0, 0.0],
        [0.0, 0.0, 1.0, 0.0],
        [-1.0, 0.0, 0.0, 0.0],
        [0.0, 0.0, 0.0, 1.0],
    ],
    dtype=np.float64,
)

# Tables to copy in dependency order (parents first so FK-disabled inserts
# remain logically consistent for downstream readers).
_COPY_ORDER = (
    "scan_session",
    "keyframe_meta",
    "branch_mark",
    "branch_edge",
    "poi_mark",
    "poi_photo",
    "interfloor_mark",
)


@dataclass(frozen=True)
class MetadataMergeSource:
    scan_id: str
    metadata_db_path: Path
    rtabmap_db_path: Path


@dataclass(frozen=True)
class MetadataMergeResult:
    output_db_path: Path
    per_source_row_counts: dict[str, dict[str, int]]
    total_rows_written: int
    missing_metadata_sources: list[str]


def merge_scan_metadata(
    *,
    sources: list[MetadataMergeSource],
    merged_rtabmap_db: Path,
    output_db: Path,
) -> MetadataMergeResult:
    sources = [s for s in sources if s.metadata_db_path.exists()]
    if not sources:
        raise FileNotFoundError(
            "no source scan_metadata.db files found to merge"
        )

    mapping_result = build_node_pose_mapping(
        source_dbs={s.scan_id: s.rtabmap_db_path for s in sources},
        merged_db=merged_rtabmap_db,
    )
    # Per (scan_id, source_node_id) → 4x4 correction in rtabmap frame.
    # Also per scan_id → fallback correction (first usable in scan).
    corrections_by_node, fallback_by_scan = _build_corrections(
        mapping_result.mappings
    )
    # Per (scan_id, source_node_id) → merged_node_id (for keyframe_meta remap)
    merged_node_id_by_src = {
        (m.source_scan_id, m.source_node_id): m.merged_node_id
        for m in mapping_result.mappings
        if m.is_usable
    }

    if output_db.exists():
        output_db.unlink()
    output_db.parent.mkdir(parents=True, exist_ok=True)

    out = sqlite3.connect(str(output_db))
    out.execute("PRAGMA foreign_keys = OFF")
    _apply_schema(out, sources[0].metadata_db_path)

    per_source: dict[str, dict[str, int]] = {}
    pk_offsets = _compute_pk_offsets(sources)

    try:
        for src in sources:
            counts = _copy_source(
                out=out,
                src=src,
                corrections_by_node=corrections_by_node,
                fallback=fallback_by_scan.get(src.scan_id),
                merged_node_id_by_src=merged_node_id_by_src,
                pk_offsets=pk_offsets[src.scan_id],
            )
            per_source[src.scan_id] = counts
        out.commit()
    finally:
        out.close()

    total = sum(sum(c.values()) for c in per_source.values())
    logger.info(
        "scan_metadata merge complete: sources=%d rows=%d output=%s",
        len(sources), total, output_db,
    )
    return MetadataMergeResult(
        output_db_path=output_db,
        per_source_row_counts=per_source,
        total_rows_written=total,
        missing_metadata_sources=[],
    )


def _apply_schema(out: sqlite3.Connection, template_db: Path) -> None:
    """Copy CREATE TABLE/INDEX statements from a representative source."""
    src = sqlite3.connect(f"file:{template_db}?mode=ro", uri=True)
    try:
        rows = src.execute(
            "SELECT type, name, sql FROM sqlite_master "
            "WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' "
            "ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END, name"
        ).fetchall()
    finally:
        src.close()
    for type_, name, sql in rows:
        try:
            out.execute(sql)
        except sqlite3.OperationalError as exc:
            logger.warning("skip %s %s: %s", type_, name, exc)


def _build_corrections(
    mappings: Iterable[NodePoseMapping],
) -> tuple[dict[tuple[str, int], np.ndarray], dict[str, np.ndarray]]:
    by_node: dict[tuple[str, int], np.ndarray] = {}
    fallback: dict[str, np.ndarray] = {}
    for m in mappings:
        if not m.is_usable or m.source_pose is None or m.optimized_pose is None:
            continue
        src = _to_4x4(m.source_pose)
        opt = _to_4x4(m.optimized_pose)
        correction = opt @ np.linalg.inv(src)
        by_node[(m.source_scan_id, m.source_node_id)] = correction
        fallback.setdefault(m.source_scan_id, correction)
    return by_node, fallback


def _compute_pk_offsets(
    sources: list[MetadataMergeSource],
) -> dict[str, dict[str, int]]:
    """Assign per-source offsets so PKs across sources never collide."""
    offsets: dict[str, dict[str, int]] = {}
    running = {tbl: 0 for tbl in ("branch_mark", "branch_edge", "poi_mark",
                                  "poi_photo", "interfloor_mark")}
    for src in sources:
        per = {tbl: running[tbl] for tbl in running}
        offsets[src.scan_id] = per
        conn = sqlite3.connect(f"file:{src.metadata_db_path}?mode=ro", uri=True)
        try:
            for tbl in running:
                try:
                    row = conn.execute(f"SELECT MAX(id) FROM {tbl}").fetchone()
                    max_id = int(row[0] or 0)
                except sqlite3.OperationalError:
                    max_id = 0
                running[tbl] += max_id
        finally:
            conn.close()
    return offsets


def _copy_source(
    *,
    out: sqlite3.Connection,
    src: MetadataMergeSource,
    corrections_by_node: dict[tuple[str, int], np.ndarray],
    fallback: np.ndarray | None,
    merged_node_id_by_src: dict[tuple[str, int], int],
    pk_offsets: dict[str, int],
) -> dict[str, int]:
    in_ = sqlite3.connect(f"file:{src.metadata_db_path}?mode=ro", uri=True)
    in_.row_factory = sqlite3.Row
    counts: dict[str, int] = {}
    # Build keyframe_seq → rtabmap_node_id lookup so marks can pick the right
    # correction.
    seq_to_rt_node: dict[int, int] = {}
    try:
        for row in in_.execute(
            "SELECT seq, rtabmap_node_id FROM keyframe_meta "
            "WHERE rtabmap_node_id IS NOT NULL"
        ):
            seq_to_rt_node[int(row["seq"])] = int(row["rtabmap_node_id"])
    except sqlite3.OperationalError:
        pass

    def correction_for_seq(seq: int) -> np.ndarray | None:
        rt = seq_to_rt_node.get(int(seq))
        if rt is not None:
            corr = corrections_by_node.get((src.scan_id, rt))
            if corr is not None:
                return corr
        return fallback

    branch_mark_id_remap: dict[int, int] = {}

    for tbl in _COPY_ORDER:
        try:
            cols = [r[1] for r in in_.execute(f"PRAGMA table_info({tbl})")]
        except sqlite3.OperationalError:
            continue
        if not cols:
            continue
        rows = in_.execute(f"SELECT * FROM {tbl}").fetchall()
        n_written = 0
        for row in rows:
            row_dict = {c: row[c] for c in cols}
            if tbl == "scan_session":
                pass  # PK is scan_id (TEXT); distinct per source — keep as-is.
            elif tbl == "keyframe_meta":
                _transform_xyz_in_place(
                    row_dict,
                    correction_for_seq(row_dict["seq"]),
                )
                rt_node = row_dict.get("rtabmap_node_id")
                if rt_node is not None:
                    merged_nid = merged_node_id_by_src.get(
                        (src.scan_id, int(rt_node))
                    )
                    row_dict["rtabmap_node_id"] = merged_nid
            elif tbl == "branch_mark":
                _transform_xyz_in_place(
                    row_dict,
                    correction_for_seq(row_dict["keyframe_seq"]),
                )
                old_id = int(row_dict["id"])
                new_id = old_id + pk_offsets["branch_mark"]
                branch_mark_id_remap[old_id] = new_id
                row_dict["id"] = new_id
                conn_text = row_dict.get("connect_node_id")
                if conn_text:
                    try:
                        ref = int(str(conn_text).strip())
                        row_dict["connect_node_id"] = str(
                            ref + pk_offsets["branch_mark"]
                        )
                    except ValueError:
                        pass
            elif tbl == "branch_edge":
                row_dict["id"] = int(row_dict["id"]) + pk_offsets["branch_edge"]
                for ref_col in ("from_node_id", "to_node_id"):
                    raw = row_dict.get(ref_col)
                    if raw is None:
                        continue
                    try:
                        ref = int(str(raw).strip())
                    except ValueError:
                        continue
                    new_ref = branch_mark_id_remap.get(
                        ref, ref + pk_offsets["branch_mark"]
                    )
                    row_dict[ref_col] = str(new_ref)
            elif tbl == "poi_mark":
                _transform_xyz_in_place(
                    row_dict,
                    correction_for_seq(row_dict["keyframe_seq"]),
                )
                row_dict["id"] = int(row_dict["id"]) + pk_offsets["poi_mark"]
            elif tbl == "poi_photo":
                row_dict["id"] = int(row_dict["id"]) + pk_offsets["poi_photo"]
                if row_dict.get("poi_mark_id") is not None:
                    row_dict["poi_mark_id"] = (
                        int(row_dict["poi_mark_id"]) + pk_offsets["poi_mark"]
                    )
            elif tbl == "interfloor_mark":
                _transform_xyz_in_place(
                    row_dict,
                    correction_for_seq(row_dict["keyframe_seq"]),
                )
                row_dict["id"] = (
                    int(row_dict["id"]) + pk_offsets["interfloor_mark"]
                )
            placeholders = ",".join("?" for _ in cols)
            out.execute(
                f"INSERT INTO {tbl} ({','.join(cols)}) VALUES ({placeholders})",
                tuple(row_dict[c] for c in cols),
            )
            n_written += 1
        counts[tbl] = n_written
    in_.close()
    return counts


def _transform_xyz_in_place(
    row_dict: dict[str, object],
    correction: np.ndarray | None,
) -> None:
    if correction is None:
        return
    if "tx" not in row_dict:
        return
    arkit = np.array(
        [
            float(row_dict["tx"] or 0.0),
            float(row_dict["ty"] or 0.0),
            float(row_dict["tz"] or 0.0),
            1.0,
        ],
        dtype=np.float64,
    )
    rt_src = _ARKIT_TO_RT @ arkit
    rt_dst = correction @ rt_src
    arkit_dst = _RT_TO_ARKIT @ rt_dst
    row_dict["tx"] = float(arkit_dst[0])
    row_dict["ty"] = float(arkit_dst[1])
    row_dict["tz"] = float(arkit_dst[2])


def _to_4x4(matrix) -> np.ndarray:
    m = np.array(matrix, dtype=np.float64)
    if m.shape == (4, 4):
        return m
    out = np.eye(4, dtype=np.float64)
    out[: m.shape[0], : m.shape[1]] = m
    return out
