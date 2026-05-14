"""SuperPoint + LightGlue localization engine."""
import asyncio
import io
import logging

import cv2
import numpy as np
import torch
from PIL import Image, ImageOps
from scipy.spatial.transform import Rotation

logger = logging.getLogger(__name__)


def _to_gray_float(img_bytes: bytes) -> np.ndarray | None:
    """Decode image bytes → grayscale float [0,1], applying EXIF orientation."""
    try:
        pil = ImageOps.exif_transpose(Image.open(io.BytesIO(img_bytes)))
        if pil.mode != 'L':
            pil = pil.convert('L')
        return np.array(pil, dtype=np.float32) / 255.0
    except Exception:
        return None


def _rotation_to_quat(R: np.ndarray):
    """Convert 3x3 rotation matrix to (qx, qy, qz, qw), qw >= 0."""
    qx, qy, qz, qw = Rotation.from_matrix(R).as_quat()
    if qw < 0:
        qx, qy, qz, qw = -qx, -qy, -qz, -qw
    return float(qx), float(qy), float(qz), float(qw)


class SuperPointEngine:
    """Localization engine using SuperPoint + LightGlue."""

    def __init__(self):
        from lightglue import LightGlue, SuperPoint
        from indoor_server.application.slam.superpoint.device import resolve_torch_device

        self._device = resolve_torch_device()
        self._extractor = SuperPoint(max_num_keypoints=1024).eval().to(self._device)
        self._matcher = LightGlue(features='superpoint').eval().to(self._device)
        logger.info(f"[SuperPoint] Engine ready on {self._device}")

    def extract_intrinsics_from_db(self, db_path: str) -> dict:
        from indoor_server.application.slam.rtabmap_intrinsics import RTABMapEngine

        return RTABMapEngine().extract_intrinsics_from_db(db_path)

    # --- feature extraction & matching ---

    def _extract(self, gray: np.ndarray) -> dict:
        tensor = torch.from_numpy(gray)[None, None].to(self._device)
        with torch.no_grad():
            return self._extractor.extract(tensor)

    def _match(self, feats0: dict, feats1: dict) -> np.ndarray:
        from lightglue.utils import rbd
        f0 = {k: v.to(self._device) for k, v in feats0.items()}
        f1 = {k: v.to(self._device) for k, v in feats1.items()}
        with torch.no_grad():
            result = self._matcher({'image0': f0, 'image1': f1})
        return rbd(result)['matches'].cpu().numpy()  # (M, 2)

    # --- localization core (runs in thread executor) ---

    def _localize_sync(
        self,
        map_id: str,
        images: list[bytes],
        intrinsics: dict | None,
        db_path: str | None,
    ) -> dict:
        from .map_manager import SuperPointMapManager

        if intrinsics is None:
            raise ValueError("intrinsics required for SuperPoint localization")

        K = np.array([
            [intrinsics['fx'], 0,              intrinsics['cx']],
            [0,               intrinsics['fy'], intrinsics['cy']],
            [0,               0,               1              ],
        ], dtype=np.float64)

        mgr = SuperPointMapManager()
        loaded = mgr.get_or_load(map_id, db_path)

        if not loaded.node_ids:
            raise ValueError("No keyframes with stored images in this map")

        # RTABMap camera-frame → OpenCV optical-frame conversion matrix
        C = np.array([[0, 0, 1], [0, -1, 0], [1, 0, 0]], dtype=np.float64)

        best: dict | None = None

        for img_idx, img_bytes in enumerate(images):
            gray = _to_gray_float(img_bytes)
            if gray is None:
                continue

            q_feats = self._extract(gray)
            q_kps = q_feats['keypoints'][0].cpu().numpy()  # (N, 2)

            from .global_descriptor import GlobalDescExtractor
            gray_uint8 = (gray * 255).clip(0, 255).astype(np.uint8)
            q_global = GlobalDescExtractor(self._device).extract(gray_uint8)  # (384,)

            candidates = loaded.top_k_candidates(q_global)

            for node_id in candidates:
                db_feats = loaded.keyframe_feats[node_id]
                world3d = loaded.keyframe_world3d[node_id]          # (M, 3)

                matches = self._match(
                    {k: v.cpu() for k, v in q_feats.items()},
                    db_feats,
                )  # (P, 2)

                if len(matches) < 4:
                    continue

                pts_2d, pts_3d = [], []
                for qi, di in matches:
                    w = world3d[di]
                    if np.any(np.isnan(w)):
                        continue
                    pts_2d.append(q_kps[qi])
                    pts_3d.append(w)

                if len(pts_3d) < 4:
                    continue

                pts_2d = np.array(pts_2d, dtype=np.float64)
                pts_3d = np.array(pts_3d, dtype=np.float64)

                ok, rvec, tvec, inliers = cv2.solvePnPRansac(
                    pts_3d, pts_2d, K, None,
                    flags=cv2.SOLVEPNP_EPNP,
                    reprojectionError=8.0,
                    confidence=0.99,
                    iterationsCount=1000,
                )

                if not ok or inliers is None or len(inliers) < 8:
                    continue

                n_in = len(inliers)
                # confidence = inlier ratio. wrong-keyframe match 자동 reject.
                # 이번 세션 검증: lenient (4) 시 wrong location 자신있게 응답.
                # 8 + 0.30 이 wrong-match 차단의 안전 임계.
                _conf_local = n_in / max(len(pts_3d), 1)
                if _conf_local < 0.30:
                    continue

                # Convert PnP result to RTABMap world pose
                # (same convention as RTABMapEngine / map_manager)
                R_w2c, _ = cv2.Rodrigues(rvec)
                R_cw = R_w2c.T @ C
                t_cw = (-R_w2c.T @ tvec).flatten()

                qx, qy, qz, qw = _rotation_to_quat(R_cw)
                confidence = min(0.99, max(0.01, n_in / len(pts_3d)))

                candidate = {
                    'num_matches': n_in,
                    'confidence': confidence,
                    'matched_image_index': img_idx,
                    'pose': {
                        'x': float(t_cw[0]), 'y': float(t_cw[1]), 'z': float(t_cw[2]),
                        'qx': qx, 'qy': qy, 'qz': qz, 'qw': qw,
                    },
                }
                if best is None or n_in > best['num_matches']:
                    best = candidate

        if best is None:
            raise ValueError("SuperPoint+LightGlue: insufficient matches")

        logger.info(
            f"[SuperPoint] map={map_id} inliers={best['num_matches']} "
            f"confidence={best['confidence']:.3f}"
        )
        return {**best, 'map_id': map_id, 'method': 'SuperPoint+LightGlue'}

    async def localize(
        self,
        map_id: str,
        images: list[bytes],
        intrinsics: dict | None = None,
        initial_pose: dict | None = None,
        db_path: str | None = None,
        **kwargs,
    ) -> dict:
        return await asyncio.to_thread(self._localize_sync, map_id, images, intrinsics, db_path)
