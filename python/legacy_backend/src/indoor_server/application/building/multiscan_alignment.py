"""Post-merge SE(3) yaw-only alignment of sub-map B onto sub-map A.

rtabmap-reprocess's multi-session graph optimization preserves each session's
own origin (sub-map B's first node stays at (0,0,0) even when cross-session
loop closures are abundant). We compensate by deriving an alignment transform
T_AB from cross-session closure links and patching every map_id != 0 Node's
pose in-place.

Algorithm:
  1. For each cross-session link (nodeA in map 0, nodeB in map !=0, observation
     T_link expressing A→B), compute the implied T_AB candidate:
       T_AB_i = pose_A_world @ T_link @ inv(pose_B_local)
  2. RANSAC over candidates with a (rotation_deg, translation_m) tolerance,
     selecting the inlier set whose T_AB agrees pairwise.
  3. Constrain to yaw-only: zero out roll/pitch and z translation (ARKit
     gravity-aligned scans live on a planar floor).
  4. Patch every Node with map_id > 0: pose_new = T_AB @ pose_old (computed
     once per session map_id by averaging the inliers belonging to that map).

We only support 2-source merges (one extra sub-map). 3+ sources would need
per-pair alignment and is deferred.
"""
from __future__ import annotations

import logging
import math
import sqlite3
import struct
from dataclasses import dataclass
from pathlib import Path

import numpy as np

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class AlignmentResult:
    status: str
    cross_pair_count: int = 0
    inlier_count: int = 0
    rotation_deg: float = 0.0
    translation_xy: tuple[float, float] = (0.0, 0.0)
    patched_node_count: int = 0
    reason: str = ""


def align_and_patch_merged(
    merged_db: Path,
    *,
    inlier_rot_deg: float = 15.0,
    inlier_trans_m: float = 3.0,
    ransac_iterations: int = 2000,
    min_inliers: int = 3,
    yaw_only: bool = False,
) -> AlignmentResult:
    """Align sub-map !=0 onto map_id=0 in `merged_db` (in-place patch)."""
    if not merged_db.exists():
        return AlignmentResult(status="skipped", reason=f"db missing: {merged_db}")
    conn = sqlite3.connect(str(merged_db))
    conn.row_factory = sqlite3.Row
    try:
        cross_links = _load_cross_session_links(conn)
        if len(cross_links) < min_inliers:
            return AlignmentResult(
                status="skipped",
                cross_pair_count=len(cross_links),
                reason=f"cross-session links {len(cross_links)} < min_inliers {min_inliers}",
            )
        # Group by sub-map id (in case >2 sub-maps; we handle each independently)
        by_map: dict[int, list[tuple[np.ndarray, np.ndarray, np.ndarray]]] = {}
        for cl in cross_links:
            by_map.setdefault(cl["map_b"], []).append(
                (cl["pose_a"], cl["link"], cl["pose_b"])
            )
        total_patched = 0
        best_inliers = 0
        rot_deg_out = 0.0
        trans_xy_out = (0.0, 0.0)
        for map_b_id, samples in by_map.items():
            if len(samples) < min_inliers:
                continue
            t_ab, inlier_mask = _ransac_se3_yaw_only(
                samples,
                rot_deg_tol=inlier_rot_deg,
                trans_tol=inlier_trans_m,
                iterations=ransac_iterations,
                yaw_only=yaw_only,
            )
            if t_ab is None:
                continue
            n_in = int(inlier_mask.sum())
            if n_in < min_inliers:
                continue
            patched = _patch_submap_poses(conn, map_b_id, t_ab)
            total_patched += patched
            if n_in > best_inliers:
                best_inliers = n_in
                rot_deg_out = math.degrees(math.atan2(t_ab[1, 0], t_ab[0, 0]))
                trans_xy_out = (float(t_ab[0, 3]), float(t_ab[1, 3]))
        if total_patched == 0:
            return AlignmentResult(
                status="skipped",
                cross_pair_count=len(cross_links),
                reason="no sub-map produced enough inliers",
            )
        conn.commit()
        return AlignmentResult(
            status="ok",
            cross_pair_count=len(cross_links),
            inlier_count=best_inliers,
            rotation_deg=rot_deg_out,
            translation_xy=trans_xy_out,
            patched_node_count=total_patched,
        )
    finally:
        conn.close()


def _load_cross_session_links(conn: sqlite3.Connection) -> list[dict]:
    """Pull all (link, pose_a, pose_b) tuples where the link crosses map_ids."""
    rows = conn.execute(
        """
        SELECT l.from_id, l.to_id, l.transform AS link_blob, l.type AS link_type,
               na.map_id AS map_a, nb.map_id AS map_b,
               na.pose AS pose_a_blob, nb.pose AS pose_b_blob
        FROM Link l
        JOIN Node na ON na.id = l.from_id
        JOIN Node nb ON nb.id = l.to_id
        WHERE l.type IN (1, 2) AND na.map_id != nb.map_id
        """
    ).fetchall()
    out: list[dict] = []
    for r in rows:
        # Always normalize direction: lower map_id as A, higher as B.
        if r["map_a"] > r["map_b"]:
            pose_a = _decode_pose_3x4(r["pose_b_blob"])
            pose_b = _decode_pose_3x4(r["pose_a_blob"])
            link = _invert_se3(_decode_pose_3x4(r["link_blob"]))
            map_a, map_b = r["map_b"], r["map_a"]
        else:
            pose_a = _decode_pose_3x4(r["pose_a_blob"])
            pose_b = _decode_pose_3x4(r["pose_b_blob"])
            link = _decode_pose_3x4(r["link_blob"])
            map_a, map_b = r["map_a"], r["map_b"]
        out.append({
            "from_id": int(r["from_id"]), "to_id": int(r["to_id"]),
            "map_a": int(map_a), "map_b": int(map_b),
            "pose_a": pose_a, "pose_b": pose_b, "link": link,
        })
    return out


def _ransac_se3_yaw_only(
    samples: list[tuple[np.ndarray, np.ndarray, np.ndarray]],
    *,
    rot_deg_tol: float,
    trans_tol: float,
    iterations: int,
    yaw_only: bool = False,
) -> tuple[np.ndarray | None, np.ndarray]:
    """RANSAC over T_AB candidates. yaw_only=True면 z-rotation + xy-translation 만
    유지(ARKit gravity-aligned 평면 가정), False면 full SE(3) (회전 6DoF + 평행이동
    3DoF 모두 추정). full SE(3)이 inlier 더 많이 잡지만 outlier 영향에 민감.
    """
    candidates = []
    for pose_a, link, pose_b in samples:
        # T_AB s.t. pose_B_world ≈ T_AB @ pose_B_local; from constraint:
        #   pose_A_world @ link = T_AB @ pose_B_local
        # => T_AB = pose_A_world @ link @ inv(pose_B_local)
        t_ab = pose_a @ link @ _invert_se3(pose_b)
        candidates.append(_constrain_yaw_only(t_ab) if yaw_only else t_ab)
    if not candidates:
        return None, np.array([], dtype=bool)
    arr = np.stack(candidates)  # (N, 4, 4)
    n = arr.shape[0]
    rng = np.random.default_rng(0)
    best_count = 0
    best_idx = None
    iters = min(iterations, n * 8)
    for _ in range(iters):
        pivot = arr[int(rng.integers(0, n))]
        diff = _pose_diff(arr, pivot)
        mask = (diff[:, 0] <= math.radians(rot_deg_tol)) & (diff[:, 1] <= trans_tol)
        count = int(mask.sum())
        if count > best_count:
            best_count = count
            best_idx = mask
    if best_idx is None or best_count == 0:
        return None, np.array([], dtype=bool)
    # Refine by averaging inliers
    inliers = arr[best_idx]
    avg = _average_se3_yaw_only(inliers) if yaw_only else _average_se3_full(inliers)
    return avg, best_idx


def _average_se3_full(arr: np.ndarray) -> np.ndarray:
    """평균 full SE(3): rotation은 SVD-projection으로 가장 가까운 SO(3),
    translation은 평균. inlier들의 회전 차이가 크지 않다는 가정 (RANSAC이
    이미 inlier 셋을 좁혀줌)."""
    R_avg = arr[:, :3, :3].mean(axis=0)
    # 가장 가까운 SO(3) projection (Frobenius 의미). U Σ V^T = R_avg → R = U V^T
    U, _, Vt = np.linalg.svd(R_avg)
    R = U @ Vt
    if np.linalg.det(R) < 0:
        Vt[2, :] *= -1
        R = U @ Vt
    t = arr[:, :3, 3].mean(axis=0)
    out = np.eye(4, dtype=np.float64)
    out[:3, :3] = R
    out[:3, 3] = t
    return out


def _patch_submap_poses(
    conn: sqlite3.Connection, map_id: int, t_ab: np.ndarray
) -> int:
    """Left-multiply every Node.pose with map_id=map_id by t_ab."""
    rows = conn.execute(
        "SELECT id, pose FROM Node WHERE map_id = ?", (map_id,)
    ).fetchall()
    count = 0
    for r in rows:
        old = _decode_pose_3x4(r["pose"])
        new = t_ab @ old
        blob = _encode_pose_3x4(new)
        conn.execute("UPDATE Node SET pose = ? WHERE id = ?", (blob, int(r["id"])))
        count += 1
    return count


def _decode_pose_3x4(blob) -> np.ndarray:
    floats = struct.unpack("<12f", bytes(blob)[:48])
    m = np.eye(4, dtype=np.float64)
    m[:3, :] = np.array(floats, dtype=np.float64).reshape(3, 4)
    return m


def _encode_pose_3x4(matrix: np.ndarray) -> bytes:
    flat = matrix[:3, :].astype(np.float32).flatten()
    return struct.pack("<12f", *flat.tolist())


def _invert_se3(m: np.ndarray) -> np.ndarray:
    out = np.eye(4, dtype=np.float64)
    r = m[:3, :3]
    t = m[:3, 3]
    out[:3, :3] = r.T
    out[:3, 3] = -r.T @ t
    return out


def _constrain_yaw_only(m: np.ndarray) -> np.ndarray:
    """Zero out pitch/roll and z translation. ARKit scans are gravity-aligned
    on a planar floor, so the inter-session transform is yaw+xy translation
    only — collapsing the other DoF reduces estimator variance."""
    out = np.eye(4, dtype=np.float64)
    yaw = math.atan2(m[1, 0], m[0, 0])
    c, s = math.cos(yaw), math.sin(yaw)
    out[0, 0] = c; out[0, 1] = -s
    out[1, 0] = s; out[1, 1] = c
    out[0, 3] = m[0, 3]
    out[1, 3] = m[1, 3]
    # z fixed at 0 (both sub-maps lie on the same floor; trust ARKit gravity)
    out[2, 3] = 0.0
    return out


def _pose_diff(arr: np.ndarray, pivot: np.ndarray) -> np.ndarray:
    """Return (N, 2) of (|rotation_diff|, |translation_diff|) for each pose
    in `arr` against `pivot`."""
    n = arr.shape[0]
    out = np.zeros((n, 2), dtype=np.float64)
    for i in range(n):
        rel = pivot @ _invert_se3(arr[i])
        # rotation angle from trace
        tr = np.clip((np.trace(rel[:3, :3]) - 1.0) / 2.0, -1.0, 1.0)
        out[i, 0] = abs(math.acos(tr))
        out[i, 1] = float(np.linalg.norm(rel[:3, 3]))
    return out


def _average_se3_yaw_only(arr: np.ndarray) -> np.ndarray:
    """Average yaw (atan2 of summed unit vectors) + average xy translation."""
    yaws = np.array([math.atan2(m[1, 0], m[0, 0]) for m in arr])
    s = float(np.sin(yaws).mean())
    c = float(np.cos(yaws).mean())
    yaw = math.atan2(s, c)
    tx = float(arr[:, 0, 3].mean())
    ty = float(arr[:, 1, 3].mean())
    out = np.eye(4, dtype=np.float64)
    cs, sn = math.cos(yaw), math.sin(yaw)
    out[0, 0] = cs; out[0, 1] = -sn
    out[1, 0] = sn; out[1, 1] = cs
    out[0, 3] = tx
    out[1, 3] = ty
    return out
