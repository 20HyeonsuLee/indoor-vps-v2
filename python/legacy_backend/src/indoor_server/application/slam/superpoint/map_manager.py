"""SuperPoint feature index built from RTABMap .db keyframe images.

Loads every keyframe image stored in the RTABMap SQLite database,
extracts SuperPoint features, and associates them with world-frame
3D positions taken from the RTABMap Feature table.

No service-layer files are modified — this is a standalone index.
"""
import io
import json
import logging
import os
import sqlite3
import struct
import threading
import time
from collections import OrderedDict
from pathlib import Path

import cv2
import numpy as np
import torch
from PIL import Image, ImageOps

logger = logging.getLogger(__name__)

MAX_CACHED_MAPS = 5
TOP_K = 5

# Bump when the build-time world3d derivation changes (e.g., ORB-mediated → depth-lift).
# Caches with older version trigger rebuild on next load.
# v3: PIL-based gray conversion (was cv2 BGR2GRAY) — caches built with v<3 invalidate.
CACHE_VERSION = 3


# ---------------------------------------------------------------------------
# DB helpers
# ---------------------------------------------------------------------------

def _parse_node_transforms(conn: sqlite3.Connection) -> dict[int, np.ndarray]:
    """Return {node_id: 3x4 world-transform matrix} from the Node table."""
    result: dict[int, np.ndarray] = {}
    for node_id, blob in conn.execute(
        "SELECT id, pose FROM Node WHERE pose IS NOT NULL"
    ):
        if not blob or len(blob) != 48:
            continue
        vals = struct.unpack('<12f', blob)
        if all(v == 0.0 for v in vals):
            continue
        result[node_id] = np.array(vals, dtype=np.float64).reshape(3, 4)
    return result


def _load_world_features(
    conn: sqlite3.Connection,
    transforms: dict[int, np.ndarray],
) -> dict[int, tuple[np.ndarray, np.ndarray]]:
    """Load RTABMap 2D keypoint positions and their world-frame 3D coordinates.

    Returns {node_id: (pos_2d (M,2) float32, world_3d (M,3) float32 with NaN rows
    where depth is unavailable)}.
    """
    rows = conn.execute(
        "SELECT node_id, pos_x, pos_y, depth_x, depth_y, depth_z FROM Feature "
        "WHERE pos_x IS NOT NULL AND pos_y IS NOT NULL"
    ).fetchall()

    buf: dict[int, tuple[list, list]] = {}
    for node_id, px, py, dx, dy, dz in rows:
        if node_id not in buf:
            buf[node_id] = ([], [])
        buf[node_id][0].append([float(px), float(py)])
        T = transforms.get(node_id)
        if T is not None and dx is not None and dy is not None and dz is not None:
            local = np.array([dx, dy, dz], dtype=np.float64)
            world = T[:, :3] @ local + T[:, 3]
            buf[node_id][1].append(world.tolist())
        else:
            buf[node_id][1].append([float('nan')] * 3)

    return {
        nid: (
            np.array(p2d, dtype=np.float32),
            np.array(w3d, dtype=np.float32),
        )
        for nid, (p2d, w3d) in buf.items()
    }


def _assign_world_3d(
    sp_kps: np.ndarray,     # (N, 2) SuperPoint keypoints in image coords
    rtab_2d: np.ndarray,    # (M, 2) RTABMap 2D feature positions
    rtab_w3d: np.ndarray,   # (M, 3) corresponding world 3D (may have NaN)
    max_px: float = 8.0,
) -> np.ndarray:
    """Vectorised nearest-neighbour assignment of world 3D to SuperPoint kps."""
    out = np.full((len(sp_kps), 3), float('nan'), dtype=np.float32)
    if len(rtab_2d) == 0:
        return out
    # (N, M) pairwise L2 distances in image space
    diffs = sp_kps[:, None, :] - rtab_2d[None, :, :]   # (N, M, 2)
    dists = np.linalg.norm(diffs, axis=2)               # (N, M)
    j_min = np.argmin(dists, axis=1)                    # (N,)
    min_dists = dists[np.arange(len(sp_kps)), j_min]
    mask = min_dists <= max_px
    out[mask] = rtab_w3d[j_min[mask]]
    return out


def _load_gray_float(conn: sqlite3.Connection, node_id: int) -> np.ndarray | None:
    """Load grayscale float [0,1] image for a node from the Data table.

    쿼리 경로(_to_gray_float)와 동일하게 PIL을 사용해야 bit-exact 동일
    이미지 → 동일 SP feature가 보장됨. cv2 BGR2GRAY는 PIL convert('L')과
    반올림이 미세하게 달라(max 1/255) SP keypoint NMS가 다른 픽셀을 골라
    self-localize에서 키포인트 매칭이 어긋남.
    """
    row = conn.execute(
        "SELECT image FROM Data WHERE id = ?", (node_id,)
    ).fetchone()
    if not row or not row[0]:
        return None
    try:
        pil = ImageOps.exif_transpose(Image.open(io.BytesIO(bytes(row[0]))))
        if pil.mode != 'L':
            pil = pil.convert('L')
        return np.array(pil, dtype=np.float32) / 255.0
    except Exception:
        return None


def decode_depth_meters(depth_blob) -> np.ndarray | None:
    """Depth blob → (H, W) float32 depth in meters.

    지원 포맷:
      1) PNG 인코딩된 RGBA 8-bit — 4 bytes를 float32로 재해석 (iOS LiDAR 일반 스캔 저장 형식).
      2) PNG 16-bit single-channel — mm 단위 가정, 1000으로 나눠 m.
      3) PNG 32-bit float single-channel — 그대로 m.
      4) **Raw float32 buffer** — 256×192 또는 192×256 등 알려진 iOS LiDAR 해상도 매치 시 직접
         np.frombuffer로 reshape. localize 요청에서 클라가 PNG 인코딩 없이 raw buffer를 보내는
         경우 대응.
    """
    if not depth_blob:
        return None
    raw = bytes(depth_blob)
    # (4) Raw float32 buffer fast path — known iOS LiDAR resolutions.
    n_f32 = len(raw) // 4
    if len(raw) % 4 == 0:
        for h, w in ((192, 256), (256, 192), (180, 240), (240, 180)):
            if h * w == n_f32:
                arr = np.frombuffer(raw, dtype=np.float32).reshape(h, w).copy()
                if np.isfinite(arr).any():
                    return arr
    # (1~3) PNG-encoded variants.
    arr_u8 = np.frombuffer(raw, dtype=np.uint8)
    img = cv2.imdecode(arr_u8, cv2.IMREAD_UNCHANGED)
    if img is None:
        return None
    if img.ndim == 3 and img.shape[2] == 4 and img.dtype == np.uint8:
        return np.ascontiguousarray(img).view(np.float32).reshape(img.shape[:2])
    if img.dtype == np.uint16:
        return img.astype(np.float32) / 1000.0
    if img.dtype == np.float32 and img.ndim == 2:
        return img
    return None


def _load_depth_meters(conn: sqlite3.Connection, node_id: int) -> np.ndarray | None:
    """Load LiDAR depth image as (H, W) float32 meters from rtabmap.db Data table."""
    row = conn.execute("SELECT depth FROM Data WHERE id = ?", (node_id,)).fetchone()
    if not row or not row[0]:
        return None
    return decode_depth_meters(bytes(row[0]))


_C_RTAB_TO_OPENCV = np.array(
    [[0, -1, 0],
     [0, 0, -1],
     [1, 0, 0]],
    dtype=np.float64,
)  # v_opencv = C @ v_rtabmap-cam
_C_OPENCV_TO_RTAB = _C_RTAB_TO_OPENCV.T  # v_rtabmap-cam = C^T @ v_opencv


def _lift_kps_with_depth(
    sp_kps: np.ndarray,            # (N, 2) SuperPoint kp in image (full-res) coords
    depth_m: np.ndarray,           # (Hd, Wd) float32 meters (may be lower-res than image)
    K: np.ndarray,                 # 3x3 image-space intrinsics (full-res, opencv convention)
    T_world_cam: np.ndarray,       # 3x4 rtabmap pose (rtabmap-cam → rtabmap-world)
    image_w: int,
    image_h: int,
) -> np.ndarray:
    """SuperPoint kp 위치에서 depth 샘플 → camera-frame 3D → world-frame 3D.

    depth resolution이 image와 다르면 비례 스케일링으로 (u, v)를 depth 좌표로
    매핑 후 bilinear 보간. invalid depth (≤0, NaN, Inf) 픽셀은 NaN.
    """
    n = sp_kps.shape[0]
    out = np.full((n, 3), np.nan, dtype=np.float32)
    if depth_m.size == 0:
        return out

    Hd, Wd = depth_m.shape[:2]
    sx = Wd / float(image_w)
    sy = Hd / float(image_h)
    fx, fy = K[0, 0], K[1, 1]
    cx, cy = K[0, 2], K[1, 2]
    R = T_world_cam[:, :3]
    t = T_world_cam[:, 3]

    u_img = sp_kps[:, 0].astype(np.float32)
    v_img = sp_kps[:, 1].astype(np.float32)
    u_d = u_img * sx
    v_d = v_img * sy
    in_bounds = (u_d >= 0) & (u_d <= Wd - 1) & (v_d >= 0) & (v_d <= Hd - 1)
    if not np.any(in_bounds):
        return out

    # Bilinear sample
    u0 = np.clip(np.floor(u_d).astype(np.int32), 0, Wd - 1)
    v0 = np.clip(np.floor(v_d).astype(np.int32), 0, Hd - 1)
    u1 = np.clip(u0 + 1, 0, Wd - 1)
    v1 = np.clip(v0 + 1, 0, Hd - 1)
    du = (u_d - u0).astype(np.float32)
    dv = (v_d - v0).astype(np.float32)
    d00 = depth_m[v0, u0]
    d01 = depth_m[v0, u1]
    d10 = depth_m[v1, u0]
    d11 = depth_m[v1, u1]
    z = (d00 * (1 - du) + d01 * du) * (1 - dv) + (d10 * (1 - du) + d11 * du) * dv

    valid = in_bounds & np.isfinite(z) & (z > 0)
    if not np.any(valid):
        return out

    z_v = z[valid].astype(np.float64)
    u_v = u_img[valid].astype(np.float64)
    v_v = v_img[valid].astype(np.float64)
    x_cam = (u_v - cx) * z_v / fx
    y_cam = (v_v - cy) * z_v / fy
    p_opencv_cam = np.stack([x_cam, y_cam, z_v], axis=1)
    # opencv-cam → rtabmap-cam (so T_world_cam, which is rtabmap-cam → rtabmap-world,
    # produces consistent world points).
    p_rtab_cam = p_opencv_cam @ _C_RTAB_TO_OPENCV  # row form of (C^T @ p_col)
    p_world = p_rtab_cam @ R.T + t
    out[valid] = p_world.astype(np.float32)
    return out


def lift_kps_camera_frame(
    sp_kps: np.ndarray,            # (N, 2) SP kp in image (full-res) coords
    depth_m: np.ndarray,           # (Hd, Wd) float32 meters (may be lower-res)
    K: np.ndarray,                 # 3x3 image-space intrinsics (full-res, opencv convention)
    image_w: int,
    image_h: int,
) -> np.ndarray:
    """Query SP kp → rtabmap-cam frame 3D. invalid depth → NaN.
    pose 적용 안함 (camera pose가 미지수일 때 사용)."""
    n = sp_kps.shape[0]
    out = np.full((n, 3), np.nan, dtype=np.float32)
    if depth_m is None or depth_m.size == 0:
        return out
    Hd, Wd = depth_m.shape[:2]
    sx = Wd / float(image_w)
    sy = Hd / float(image_h)
    fx, fy = K[0, 0], K[1, 1]
    cx, cy = K[0, 2], K[1, 2]

    u_img = sp_kps[:, 0].astype(np.float32)
    v_img = sp_kps[:, 1].astype(np.float32)
    u_d = u_img * sx
    v_d = v_img * sy
    in_bounds = (u_d >= 0) & (u_d <= Wd - 1) & (v_d >= 0) & (v_d <= Hd - 1)
    if not np.any(in_bounds):
        return out

    u0 = np.clip(np.floor(u_d).astype(np.int32), 0, Wd - 1)
    v0 = np.clip(np.floor(v_d).astype(np.int32), 0, Hd - 1)
    u1 = np.clip(u0 + 1, 0, Wd - 1)
    v1 = np.clip(v0 + 1, 0, Hd - 1)
    du = (u_d - u0).astype(np.float32)
    dv = (v_d - v0).astype(np.float32)
    d00 = depth_m[v0, u0]
    d01 = depth_m[v0, u1]
    d10 = depth_m[v1, u0]
    d11 = depth_m[v1, u1]
    z = (d00 * (1 - du) + d01 * du) * (1 - dv) + (d10 * (1 - du) + d11 * du) * dv

    valid = in_bounds & np.isfinite(z) & (z > 0)
    if not np.any(valid):
        return out

    z_v = z[valid].astype(np.float64)
    u_v = u_img[valid].astype(np.float64)
    v_v = v_img[valid].astype(np.float64)
    x_cam = (u_v - cx) * z_v / fx
    y_cam = (v_v - cy) * z_v / fy
    p_opencv = np.stack([x_cam, y_cam, z_v], axis=1)
    p_rtab = p_opencv @ _C_RTAB_TO_OPENCV  # opencv-cam → rtabmap-cam (row form)
    out[valid] = p_rtab.astype(np.float32)
    return out


def _parse_calibration_K_and_local(blob: bytes) -> tuple[np.ndarray, np.ndarray]:
    """RTABMap 0.23.x calibration BLOB (164B) → (K 3x3, local_transform 3x4).

    Layout (write_rtabmap_db 와 동일):
      [44..115]  9x float64 = K matrix (row-major)
      [116..163] 12x float32 = local_transform (row-major)
    """
    import struct
    if len(blob) < 116:
        raise ValueError(f"calibration blob too short: {len(blob)} bytes")
    K_vals = struct.unpack('<9d', blob[44:44 + 72])
    K = np.array(K_vals, dtype=np.float64).reshape(3, 3)
    if len(blob) >= 164:
        lt_vals = struct.unpack('<12f', blob[116:116 + 48])
        local_transform = np.array(lt_vals, dtype=np.float64).reshape(3, 4)
    else:
        local_transform = np.zeros((3, 4))
        local_transform[:3, :3] = np.eye(3)
    return K, local_transform


def _smart_select_keyframes(
    all_ids: list[int],
    transforms: dict[int, np.ndarray],
    min_translation_m: float = 0.30,
    min_rotation_deg: float = 15.0,
    force_keep_rotation_deg: float = 45.0,
) -> list[int]:
    """Translation/rotation 누적 기반 keyframe subsample.

    인접 frame 간 baseline 이 너무 작으면 triangulation 부정확. 같은 view 를
    오래 본 frame 도 redundant. 마지막 keep 된 frame 으로부터:
      - translation ≥ min_translation_m  → keep
      - rotation ≥ min_rotation_deg     → keep
      - rotation ≥ force_keep_rotation_deg → keep (큰 방향 전환은 무조건)
    셋 다 미달이면 redundant.
    """
    import math

    def _yaw(R: np.ndarray) -> float:
        return math.atan2(float(R[1, 0]), float(R[0, 0]))

    def _rot_diff(R_a: np.ndarray, R_b: np.ndarray) -> float:
        # axis-angle 거리 (안정적). trace((R_a^T R_b)) → angle
        R = R_b @ R_a.T
        cos_t = max(-1.0, min(1.0, (np.trace(R) - 1.0) / 2.0))
        return math.degrees(math.acos(cos_t))

    keep: list[int] = []
    last_T: np.ndarray | None = None
    for nid in all_ids:
        T34 = transforms.get(nid)
        if T34 is None:
            continue
        if last_T is None:
            keep.append(nid)
            last_T = T34
            continue
        d = float(np.linalg.norm(T34[:3, 3] - last_T[:3, 3]))
        rot = _rot_diff(last_T[:3, :3], T34[:3, :3])
        if (
            d >= min_translation_m
            or rot >= min_rotation_deg
            or rot >= force_keep_rotation_deg
        ):
            keep.append(nid)
            last_T = T34
    return keep


# ---------------------------------------------------------------------------
# Loaded map
# ---------------------------------------------------------------------------

class SuperPointLoadedMap:
    """SuperPoint feature index for all keyframes in one RTABMap DB."""

    def __init__(
        self,
        map_id: str,
        db_path: str,
        device: torch.device,
        cache_dir: Path | None = None,
    ):
        self.map_id = map_id
        self.db_path = db_path
        self.device = device
        self._cache_dir = cache_dir

        self.node_ids: list[int] = []
        # CPU tensors: {'keypoints': (1,N,2), 'descriptors': (1,N,256), 'image_size': (1,2)}
        self.keyframe_feats: dict[int, dict] = {}
        # (N, 3) world 3D per keyframe keypoint; NaN where unavailable
        self.keyframe_world3d: dict[int, np.ndarray] = {}
        # (K, 384) DINOv2 global descriptors
        self.global_descs: torch.Tensor | None = None

        if not self._try_load_cache():
            self._build_index()
            self._save_cache()

    # ------------------------------------------------------------------
    # Disk cache (mmap-friendly raw .npy + meta.json)
    # ------------------------------------------------------------------

    def _current_db_mtime(self) -> float | None:
        try:
            return os.path.getmtime(self.db_path)
        except OSError:
            return None

    def _try_load_cache(self) -> bool:
        """Load from disk cache if cache_dir exists and db_mtime matches.

        Returns True when cache was loaded successfully.
        """
        if self._cache_dir is None:
            return False
        meta_path = self._cache_dir / "meta.json"
        if not meta_path.exists():
            return False
        try:
            meta = json.loads(meta_path.read_text())
        except (json.JSONDecodeError, OSError) as exc:
            logger.warning("[SuperPoint] cache meta unreadable: %s — skipping", exc)
            return False

        current_mtime = self._current_db_mtime()
        if current_mtime is None or abs(meta.get("db_mtime", -1) - current_mtime) > 1.0:
            logger.info(
                "[SuperPoint] map '%s' cache stale (mtime mismatch) — rebuilding",
                self.map_id,
            )
            return False

        if meta.get("cache_version", 1) < CACHE_VERSION:
            logger.info(
                "[SuperPoint] map '%s' cache version %s < %s — rebuilding",
                self.map_id, meta.get("cache_version", 1), CACHE_VERSION,
            )
            return False

        required = ["keypoints.npy", "descriptors.npy", "frame_offsets.npy",
                    "world_points.npy", "global_descriptors.npy"]
        for fname in required:
            if not (self._cache_dir / fname).exists():
                logger.warning("[SuperPoint] cache file missing: %s — rebuilding", fname)
                return False

        try:
            t0 = time.time()
            kps_all = np.load(str(self._cache_dir / "keypoints.npy"), mmap_mode="r")
            descs_all = np.load(str(self._cache_dir / "descriptors.npy"), mmap_mode="r")
            offsets = np.load(str(self._cache_dir / "frame_offsets.npy"), mmap_mode="r")
            world_all = np.load(str(self._cache_dir / "world_points.npy"), mmap_mode="r")
            global_descs_arr = np.load(
                str(self._cache_dir / "global_descriptors.npy"), mmap_mode="r"
            )
        except Exception as exc:
            logger.warning("[SuperPoint] cache load failed: %s — rebuilding", exc)
            return False

        frame_ids: list[int] = [int(x) for x in meta["frame_ids"]]
        self.node_ids = frame_ids

        for i, nid in enumerate(frame_ids):
            start = int(offsets[i])
            end = int(offsets[i + 1])
            kps = np.array(kps_all[start:end], dtype=np.float32)
            descs = np.array(descs_all[start:end], dtype=np.float32)
            img_size = meta["image_sizes"][i]
            self.keyframe_feats[nid] = {
                "keypoints": torch.from_numpy(kps).unsqueeze(0),
                "descriptors": torch.from_numpy(descs).unsqueeze(0),
                "image_size": torch.tensor([[img_size[0], img_size[1]]], dtype=torch.int),
            }
            self.keyframe_world3d[nid] = np.array(world_all[start:end], dtype=np.float32)

        if len(global_descs_arr):
            self.global_descs = torch.from_numpy(np.array(global_descs_arr, dtype=np.float32))

        logger.info(
            "[SuperPoint] map '%s' loaded from disk cache in %.2fs: %d frames",
            self.map_id,
            time.time() - t0,
            len(frame_ids),
        )
        return True

    def _save_cache(self) -> None:
        """Persist feature index to disk as raw .npy + meta.json."""
        if self._cache_dir is None or not self.node_ids:
            return
        try:
            self._cache_dir.mkdir(parents=True, exist_ok=True)
            kps_list: list[np.ndarray] = []
            descs_list: list[np.ndarray] = []
            world_list: list[np.ndarray] = []
            offsets: list[int] = [0]
            image_sizes: list[list[int]] = []

            for nid in self.node_ids:
                feats = self.keyframe_feats[nid]
                kps = feats["keypoints"][0].numpy()        # (N, 2)
                descs = feats["descriptors"][0].numpy()    # (N, 256)
                w3d = self.keyframe_world3d[nid]           # (N, 3)
                kps_list.append(kps)
                descs_list.append(descs)
                world_list.append(w3d)
                offsets.append(offsets[-1] + len(kps))
                sz = feats.get("image_size")
                if sz is not None:
                    image_sizes.append(sz[0].tolist())
                else:
                    image_sizes.append([0, 0])

            np.save(str(self._cache_dir / "keypoints.npy"), np.concatenate(kps_list, axis=0))
            np.save(str(self._cache_dir / "descriptors.npy"), np.concatenate(descs_list, axis=0))
            np.save(str(self._cache_dir / "frame_offsets.npy"), np.array(offsets, dtype=np.int64))
            np.save(str(self._cache_dir / "world_points.npy"), np.concatenate(world_list, axis=0))

            if self.global_descs is not None:
                np.save(
                    str(self._cache_dir / "global_descriptors.npy"),
                    self.global_descs.numpy(),
                )
            else:
                np.save(
                    str(self._cache_dir / "global_descriptors.npy"),
                    np.empty((0, 384), dtype=np.float32),
                )

            meta = {
                "map_id": self.map_id,
                "db_path": self.db_path,
                "db_mtime": self._current_db_mtime(),
                "cache_version": CACHE_VERSION,
                "frame_ids": self.node_ids,
                "image_sizes": image_sizes,
                "total_keypoints": int(offsets[-1]),
            }
            (self._cache_dir / "meta.json").write_text(json.dumps(meta))
            logger.info(
                "[SuperPoint] map '%s' cache saved to %s (%d frames, %d kp)",
                self.map_id,
                self._cache_dir,
                len(self.node_ids),
                offsets[-1],
            )
        except Exception as exc:
            logger.warning("[SuperPoint] cache save failed: %s", exc)

    # ------------------------------------------------------------------
    # Index build
    # ------------------------------------------------------------------

    def _build_index(self):
        from lightglue import SuperPoint

        extractor = SuperPoint(max_num_keypoints=1024).eval().to(self.device)
        t0 = time.time()

        conn = sqlite3.connect(self.db_path)
        try:
            all_ids = [r[0] for r in conn.execute(
                "SELECT id FROM Node WHERE pose IS NOT NULL ORDER BY id"
            ).fetchall()]

            transforms = _parse_node_transforms(conn)

            # Read K from any node's calibration blob (assume constant intrinsics
            # across the scan — single camera, no zoom).
            K_for_lift: np.ndarray | None = None
            for cal_id in all_ids:
                cal_row = conn.execute(
                    "SELECT calibration FROM Data WHERE id = ?", (cal_id,)
                ).fetchone()
                if cal_row and cal_row[0]:
                    try:
                        K_for_lift, _ = _parse_calibration_K_and_local(bytes(cal_row[0]))
                        break
                    except ValueError:
                        continue
            if K_for_lift is None:
                logger.warning("[SuperPoint] '%s': no calibration → fallback to ORB-derived world3d",
                               self.map_id)
                world_feats = _load_world_features(conn, transforms)
            else:
                world_feats = None  # depth-direct path

            global_descs: list[torch.Tensor] = []
            from .global_descriptor import GlobalDescExtractor
            global_desc_ext = GlobalDescExtractor(self.device)

            depth_hits = 0
            depth_misses = 0
            for node_id in all_ids:
                img = _load_gray_float(conn, node_id)
                if img is None:
                    continue

                tensor = torch.from_numpy(img)[None, None].to(self.device)
                with torch.no_grad():
                    feats = extractor.extract(tensor)

                cpu = {k: v.cpu() for k, v in feats.items()}
                self.keyframe_feats[node_id] = cpu
                self.node_ids.append(node_id)

                # DINOv2 global descriptor (384-dim) instead of mean SuperPoint (256-dim)
                img_uint8 = (img * 255).clip(0, 255).astype(np.uint8)
                global_descs.append(global_desc_ext.extract(img_uint8))  # (384,)

                sp_kps = cpu['keypoints'][0].numpy()   # (N, 2)
                world3d: np.ndarray | None = None

                # 1. Direct depth lift (preferred — uses LiDAR depth at SP kp locations)
                if K_for_lift is not None and node_id in transforms:
                    depth_m = _load_depth_meters(conn, node_id)
                    if depth_m is not None:
                        world3d = _lift_kps_with_depth(
                            sp_kps, depth_m, K_for_lift, transforms[node_id],
                            image_w=img.shape[1], image_h=img.shape[0],
                        )
                        if np.isfinite(world3d).any():
                            depth_hits += 1
                        else:
                            depth_misses += 1

                # 2. Fallback to ORB feature 8px-nearest path
                if world3d is None and world_feats is not None and node_id in world_feats:
                    r2d, w3d = world_feats[node_id]
                    world3d = _assign_world_3d(sp_kps, r2d, w3d)

                if world3d is None:
                    world3d = np.full((len(sp_kps), 3), float('nan'), dtype=np.float32)
                self.keyframe_world3d[node_id] = world3d

                n = len(self.node_ids)
                if n % 100 == 0:
                    logger.info(
                        f"[SuperPoint] '{self.map_id}': indexed {n}/{len(all_ids)} frames"
                    )
            logger.info(
                "[SuperPoint] '%s': depth-lift hits=%d misses=%d",
                self.map_id, depth_hits, depth_misses,
            )
        finally:
            conn.close()

        if global_descs:
            self.global_descs = torch.stack(global_descs)   # (K, 384)

        n_with_3d = sum(
            1 for v in self.keyframe_world3d.values()
            if not np.all(np.isnan(v))
        )
        logger.info(
            f"[SuperPoint] Map '{self.map_id}' indexed in {time.time()-t0:.1f}s: "
            f"{len(self.node_ids)} frames, {n_with_3d} with 3D coverage"
        )

        # Fallback: RTAB-Map Feature 에 depth 가 없어 3D coverage 가 0 인 경우
        # 이웃 keyframe pair multi-view triangulation 으로 world 3D 추정
        if n_with_3d == 0 and len(self.node_ids) >= 2:
            logger.info(
                "[SuperPoint] No depth coverage from RTAB-Map — running multi-view triangulation"
            )
            t1 = time.time()
            self._triangulate_via_multi_view()
            n_with_3d = sum(
                1 for v in self.keyframe_world3d.values()
                if not np.all(np.isnan(v))
            )
            logger.info(
                f"[SuperPoint] Triangulation done in {time.time()-t1:.1f}s: "
                f"{n_with_3d}/{len(self.node_ids)} frames with 3D coverage"
            )

            # ML depth fill — 실험적. NaN 은 줄지만 PnP inliers 안 늘어 측위 정확도 역효과.
            # opt-in: env INDOOR_ENABLE_ML_DEPTH_FILL=1 일 때만 활성.
            import os as _os
            if _os.environ.get("INDOOR_ENABLE_ML_DEPTH_FILL") == "1":
                try:
                    t2 = time.time()
                    n_filled, n_attempted = self._fill_nan_with_ml_depth()
                    logger.info(
                        f"[SuperPoint] ML depth fill done in {time.time()-t2:.1f}s: "
                        f"filled {n_filled}/{n_attempted} NaN keypoints"
                    )
                except Exception as exc:
                    logger.warning(
                        f"[SuperPoint] ML depth fill skipped: {type(exc).__name__}: {exc}"
                    )

    def _triangulate_via_multi_view(
        self,
        neighbor_offsets: tuple[int, ...] = (-2, -1, 1, 2),
        min_matches: int = 12,
        min_depth_m: float = 0.2,
        max_depth_m: float = 30.0,
    ) -> None:
        """이웃 keyframe pair LightGlue 매칭 + cv2.triangulatePoints → world 3D.

        Node.pose 는 base_link → world (3x4). Calibration BLOB 의 local_transform
        은 base_link → camera_optical 변환. P = K @ T_world_to_optical.
        """
        from lightglue import LightGlue
        from lightglue.utils import rbd

        conn = sqlite3.connect(self.db_path)
        try:
            transforms = _parse_node_transforms(conn)  # base→world (3x4)
            # K + local_transform 추출 (모든 keyframe 동일 가정 — 첫 keyframe 사용)
            calib_row = conn.execute(
                "SELECT calibration FROM Data WHERE id = ? LIMIT 1",
                (self.node_ids[0],),
            ).fetchone()
            if not calib_row or not calib_row[0]:
                logger.warning("[SuperPoint] No calibration in Data — skipping triangulation")
                return
            K, local_transform = _parse_calibration_K_and_local(bytes(calib_row[0]))
            C = local_transform[:3, :3]  # base → optical (3x3)
        finally:
            conn.close()

        matcher = LightGlue(features='superpoint').eval().to(self.device)

        # 누적 buffer: keyframe id → kp index → list of triangulated 3D points
        accum: dict[int, list[list[np.ndarray]]] = {}
        for nid in self.node_ids:
            kp_count = self.keyframe_feats[nid]['keypoints'].shape[1]
            accum[nid] = [[] for _ in range(kp_count)]

        n = len(self.node_ids)
        pair_processed = 0
        with torch.no_grad():
            for i in range(n):
                nid_i = self.node_ids[i]
                if nid_i not in transforms:
                    continue
                T_bi_w = np.eye(4, dtype=np.float64)
                T_bi_w[:3, :] = transforms[nid_i]
                T_w_bi = np.linalg.inv(T_bi_w)
                # world → optical_i
                R_wo_i = C @ T_w_bi[:3, :3]
                t_wo_i = C @ T_w_bi[:3, 3]
                P_i = K @ np.hstack([R_wo_i, t_wo_i.reshape(3, 1)])

                feats_i = {k: v.to(self.device) for k, v in self.keyframe_feats[nid_i].items()}
                kps_i_np = self.keyframe_feats[nid_i]['keypoints'][0].cpu().numpy()

                for offset in neighbor_offsets:
                    j = i + offset
                    if j < 0 or j >= n:
                        continue
                    nid_j = self.node_ids[j]
                    if nid_j not in transforms:
                        continue
                    T_bj_w = np.eye(4, dtype=np.float64)
                    T_bj_w[:3, :] = transforms[nid_j]
                    T_w_bj = np.linalg.inv(T_bj_w)
                    R_wo_j = C @ T_w_bj[:3, :3]
                    t_wo_j = C @ T_w_bj[:3, 3]
                    P_j = K @ np.hstack([R_wo_j, t_wo_j.reshape(3, 1)])

                    feats_j = {k: v.to(self.device) for k, v in self.keyframe_feats[nid_j].items()}
                    kps_j_np = self.keyframe_feats[nid_j]['keypoints'][0].cpu().numpy()

                    result = matcher({'image0': feats_i, 'image1': feats_j})
                    matches = rbd(result)['matches'].cpu().numpy()
                    if len(matches) < min_matches:
                        continue

                    pts_i = kps_i_np[matches[:, 0]].T.astype(np.float64)  # (2, M)
                    pts_j = kps_j_np[matches[:, 1]].T.astype(np.float64)

                    X_h = cv2.triangulatePoints(P_i, P_j, pts_i, pts_j)  # (4, M)
                    valid_w = np.abs(X_h[3]) > 1e-9
                    X = (X_h[:3, valid_w] / X_h[3, valid_w]).T  # (M', 3) world

                    if len(X) == 0:
                        continue

                    # 두 카메라 모두에서 positive depth & in-range
                    Xi_opt = (R_wo_i @ X.T).T + t_wo_i  # optical_i frame
                    Xj_opt = (R_wo_j @ X.T).T + t_wo_j
                    di = Xi_opt[:, 2]
                    dj = Xj_opt[:, 2]
                    in_range = (
                        (di > min_depth_m) & (di < max_depth_m) &
                        (dj > min_depth_m) & (dj < max_depth_m)
                    )

                    matches_valid_idx = np.flatnonzero(valid_w)[in_range]
                    X_kept = X[in_range]
                    for k_idx, mi in zip(matches[matches_valid_idx, 0], X_kept, strict=False):
                        accum[nid_i][int(k_idx)].append(mi.astype(np.float32))
                    pair_processed += 1

        # 각 keyframe 의 kp 별 median (또는 평균) 으로 final world 3D
        for nid in self.node_ids:
            kp_count = len(accum[nid])
            arr = np.full((kp_count, 3), np.nan, dtype=np.float32)
            for kp_idx, points in enumerate(accum[nid]):
                if points:
                    arr[kp_idx] = np.median(np.stack(points, axis=0), axis=0)
            self.keyframe_world3d[nid] = arr

        logger.info(
            "[SuperPoint] triangulation: pairs=%d (neighbor offsets=%s)",
            pair_processed,
            neighbor_offsets,
        )

    def _fill_nan_with_ml_depth(
        self,
        model_id: str = "depth-anything/Depth-Anything-V2-Base-hf",
        min_anchors: int = 20,
        ransac_iters: int = 400,
        ransac_inlier_thresh_rel: float = 0.08,
        depth_min_m: float = 0.2,
        depth_max_m: float = 30.0,
    ) -> tuple[int, int]:
        """ML monocular depth 로 NaN keypoint 의 world 3D 채움.

        흐름:
          1. Depth Anything V2 로 keyframe 별 relative depth map 추론.
          2. multi-view triangulated 3D 가 있는 keypoint (anchor) 의 absolute depth 와
             ML depth 의 (a, b) 를 RANSAC linear fit: abs = a * rel + b.
          3. NaN keypoint 의 (u, v) 에서 rel depth → absolute → unproject → world 3D.

        Returns: (filled_count, attempted_count)
        """
        from PIL import Image
        from transformers import pipeline

        # 모델 로드 (CUDA 시 device=0)
        device_arg = 0 if str(self.device).startswith("cuda") else -1
        depth_pipe = pipeline("depth-estimation", model=model_id, device=device_arg)

        # calibration + transforms
        conn = sqlite3.connect(self.db_path)
        try:
            transforms = _parse_node_transforms(conn)
            calib_row = conn.execute(
                "SELECT calibration FROM Data WHERE id = ? LIMIT 1",
                (self.node_ids[0],),
            ).fetchone()
            if not calib_row or not calib_row[0]:
                raise RuntimeError("calibration BLOB missing")
            K, local_transform = _parse_calibration_K_and_local(bytes(calib_row[0]))
            C = local_transform[:3, :3]
        finally:
            conn.close()

        n_filled = 0
        n_attempted = 0
        n_frames_aligned = 0
        n_frames_no_anchor = 0

        for nid in self.node_ids:
            if nid not in transforms:
                continue
            # 이미지 로드 (다른 connection — 동시성 안전)
            with sqlite3.connect(self.db_path) as conn:
                img = _load_gray_float(conn, nid)
            if img is None:
                continue

            # ML depth 추론
            img_uint8 = (img * 255).clip(0, 255).astype(np.uint8)
            pil = Image.fromarray(img_uint8).convert("RGB")
            try:
                out = depth_pipe(pil)
                rel_depth = np.array(out["depth"], dtype=np.float32)
            except Exception as exc:
                logger.warning(f"[ML depth] inference failed for node={nid}: {exc}")
                continue
            if rel_depth.shape != img.shape:
                rel_depth = cv2.resize(
                    rel_depth, (img.shape[1], img.shape[0]),
                    interpolation=cv2.INTER_LINEAR,
                )

            # camera pose (world → optical)
            T_b_w = np.eye(4, dtype=np.float64)
            T_b_w[:3, :] = transforms[nid]
            T_w_b = np.linalg.inv(T_b_w)
            R_wo = C @ T_w_b[:3, :3]
            t_wo = C @ T_w_b[:3, 3]

            kps = self.keyframe_feats[nid]['keypoints'][0].cpu().numpy().astype(np.float32)
            world3d = self.keyframe_world3d[nid]
            valid = ~np.isnan(world3d).any(axis=1)
            n_anchor = int(valid.sum())
            if n_anchor < min_anchors:
                n_frames_no_anchor += 1
                continue

            # anchor 의 absolute depth (optical z)
            X_opt_anchor = (R_wo @ world3d[valid].T).T + t_wo
            abs_d = X_opt_anchor[:, 2]

            # rel depth at anchor (u, v)
            uv_anchor = kps[valid]
            uv_int = np.round(uv_anchor).astype(int)
            uv_int[:, 0] = np.clip(uv_int[:, 0], 0, rel_depth.shape[1] - 1)
            uv_int[:, 1] = np.clip(uv_int[:, 1], 0, rel_depth.shape[0] - 1)
            rel_at = rel_depth[uv_int[:, 1], uv_int[:, 0]]

            # 이상값 제외 (anchor depth 가 양수 + 범위 내)
            ok_anchor = (abs_d > depth_min_m) & (abs_d < depth_max_m) & np.isfinite(rel_at)
            if int(ok_anchor.sum()) < min_anchors:
                n_frames_no_anchor += 1
                continue
            rel_at = rel_at[ok_anchor]
            abs_d = abs_d[ok_anchor]

            # RANSAC linear fit (a, b)
            best_inliers = 0
            best_a = best_b = None
            rng = np.random.default_rng(seed=int(nid))
            for _ in range(ransac_iters):
                idx = rng.choice(len(rel_at), size=2, replace=False)
                r1, r2 = rel_at[idx]
                a1, a2 = abs_d[idx]
                if abs(r1 - r2) < 1e-6:
                    continue
                a = (a1 - a2) / (r1 - r2)
                b = a1 - a * r1
                pred = a * rel_at + b
                err = np.abs(pred - abs_d)
                inlier = err < (np.abs(abs_d) * ransac_inlier_thresh_rel + 0.05)
                if int(inlier.sum()) > best_inliers:
                    best_inliers = int(inlier.sum())
                    best_a, best_b = a, b
            if best_a is None or best_inliers < min_anchors // 2:
                n_frames_no_anchor += 1
                continue
            # least squares re-fit on inliers
            pred = best_a * rel_at + best_b
            err = np.abs(pred - abs_d)
            inlier_mask = err < (np.abs(abs_d) * ransac_inlier_thresh_rel + 0.05)
            if int(inlier_mask.sum()) >= 2:
                A = np.stack([rel_at[inlier_mask], np.ones(inlier_mask.sum())], axis=1)
                sol, *_ = np.linalg.lstsq(A, abs_d[inlier_mask], rcond=None)
                best_a, best_b = float(sol[0]), float(sol[1])
            n_frames_aligned += 1

            # NaN keypoint 채우기
            nan_idx = np.flatnonzero(~valid)
            if len(nan_idx) == 0:
                continue
            n_attempted += len(nan_idx)
            uv_nan = kps[nan_idx]
            uv_nan_int = np.round(uv_nan).astype(int)
            uv_nan_int[:, 0] = np.clip(uv_nan_int[:, 0], 0, rel_depth.shape[1] - 1)
            uv_nan_int[:, 1] = np.clip(uv_nan_int[:, 1], 0, rel_depth.shape[0] - 1)
            rel_nan = rel_depth[uv_nan_int[:, 1], uv_nan_int[:, 0]]
            abs_nan = best_a * rel_nan + best_b

            ok_d = (abs_nan > depth_min_m) & (abs_nan < depth_max_m) & np.isfinite(abs_nan)
            if not ok_d.any():
                continue

            u = uv_nan[:, 0]
            v = uv_nan[:, 1]
            x_opt = (u - K[0, 2]) * abs_nan / K[0, 0]
            y_opt = (v - K[1, 2]) * abs_nan / K[1, 1]
            z_opt = abs_nan
            pts_opt = np.stack([x_opt, y_opt, z_opt], axis=1)

            # optical → world: X_w = R_wo^T (X_opt - t_wo)
            R_ow = R_wo.T
            pts_world = (R_ow @ (pts_opt - t_wo).T).T

            for k, w_pt, ok in zip(nan_idx, pts_world, ok_d, strict=False):
                if ok and np.all(np.isfinite(w_pt)):
                    self.keyframe_world3d[nid][k] = w_pt.astype(np.float32)
                    n_filled += 1

        logger.info(
            f"[ML depth] frames_aligned={n_frames_aligned} "
            f"frames_skipped(no_anchor)={n_frames_no_anchor}"
        )
        return n_filled, n_attempted

    def top_k_candidates(
        self, q_desc_mean: torch.Tensor, k: int = TOP_K
    ) -> list[int]:
        """Return top-K node IDs by cosine similarity of mean descriptors."""
        if self.global_descs is None or not self.node_ids:
            return self.node_ids[:k]
        sims = torch.cosine_similarity(
            q_desc_mean.unsqueeze(0), self.global_descs
        )
        k = min(k, len(self.node_ids))
        indices = sims.topk(k).indices.tolist()
        return [self.node_ids[i] for i in indices]


# ---------------------------------------------------------------------------
# Singleton manager
# ---------------------------------------------------------------------------

class SuperPointMapManager:
    """Singleton LRU cache for SuperPointLoadedMap instances."""

    _instance = None
    _lock = threading.Lock()

    def __new__(cls):
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    inst = super().__new__(cls)
                    inst._maps: OrderedDict[str, SuperPointLoadedMap] = OrderedDict()
                    from indoor_server.application.slam.superpoint.device import resolve_torch_device

                    inst._device = resolve_torch_device()
                    # map_id 별 indexing 동시 진행 방지 lock
                    inst._build_locks: dict[str, threading.Lock] = {}
                    inst._build_locks_guard = threading.Lock()
                    cls._instance = inst
        return cls._instance

    def _get_build_lock(self, map_id: str) -> threading.Lock:
        with self._build_locks_guard:
            if map_id not in self._build_locks:
                self._build_locks[map_id] = threading.Lock()
            return self._build_locks[map_id]

    @property
    def device(self) -> torch.device:
        return self._device

    def get_or_load(
        self, map_id: str, db_path: str | None = None
    ) -> SuperPointLoadedMap:
        # active scan 이 바뀌었거나 db 파일 mtime 이 바뀐 경우 캐시 invalidate
        if map_id in self._maps:
            cached = self._maps[map_id]
            try:
                cached_mtime = getattr(cached, "_db_mtime", None)
                if db_path is not None and str(cached.db_path) != str(db_path):
                    logger.info(
                        f"[SuperPoint] map '{map_id}' db_path changed "
                        f"({cached.db_path} → {db_path}) — invalidating cache"
                    )
                    del self._maps[map_id]
                else:
                    current_mtime = os.path.getmtime(cached.db_path) if cached.db_path else None
                    if cached_mtime is not None and current_mtime != cached_mtime:
                        logger.info(
                            f"[SuperPoint] map '{map_id}' db file modified "
                            f"(mtime {cached_mtime} → {current_mtime}) — invalidating cache"
                        )
                        del self._maps[map_id]
                    else:
                        self._maps.move_to_end(map_id)
                        return cached
            except Exception as exc:
                logger.warning(f"[SuperPoint] cache validation failed: {exc} — invalidating")
                self._maps.pop(map_id, None)

        if db_path is None:
            raise ValueError(f"Map '{map_id}' not cached and no db_path provided")

        # map_id 별 lock — 동시 요청 시 첫 thread 만 indexing, 나머지는 대기 후 cache hit
        build_lock = self._get_build_lock(map_id)
        with build_lock:
            # double-check: lock 대기 중 다른 thread 가 이미 indexing 했을 수 있음
            if map_id in self._maps:
                cached = self._maps[map_id]
                if str(cached.db_path) == str(db_path):
                    self._maps.move_to_end(map_id)
                    return cached

            cache_dir = Path(db_path).parent / "superpoint_index"
            m = SuperPointLoadedMap(map_id, db_path, self._device, cache_dir=cache_dir)
            try:
                m._db_mtime = os.path.getmtime(db_path)
            except Exception:
                m._db_mtime = None
            self._maps[map_id] = m
            while len(self._maps) > MAX_CACHED_MAPS:
                evicted, _ = self._maps.popitem(last=False)
                logger.info(f"[SuperPoint] Evicted map '{evicted}' from cache")
            return m
