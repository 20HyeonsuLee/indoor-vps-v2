"""SuperPoint + LightGlue localization engine."""
import asyncio
import io
import logging
import math

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


def _matrix_pose_to_dict(T: np.ndarray) -> dict:
    qx, qy, qz, qw = _rotation_to_quat(T[:3, :3])
    t = T[:3, 3]
    return {
        'x': float(t[0]), 'y': float(t[1]), 'z': float(t[2]),
        'qx': qx, 'qy': qy, 'qz': qz, 'qw': qw,
    }


def _pose_position(pose: dict) -> np.ndarray:
    return np.array([pose['x'], pose['y'], pose['z']], dtype=np.float64)


def _pose_rotation(pose: dict) -> Rotation:
    return Rotation.from_quat([pose['qx'], pose['qy'], pose['qz'], pose['qw']])


def _yaw_from_rotation(rot: Rotation) -> float:
    R = rot.as_matrix()
    return math.atan2(float(R[1, 0]), float(R[0, 0]))


def _wrap_angle(rad: float) -> float:
    return math.atan2(math.sin(rad), math.cos(rad))


def _yaw_diff(a: float, b: float) -> float:
    return abs(_wrap_angle(a - b))


def _blend_pose(raw_pose: dict, anchor_pose: dict, pos_alpha: float, rot_alpha: float) -> dict:
    raw_pos = _pose_position(raw_pose)
    anchor_pos = _pose_position(anchor_pose)
    pos = anchor_pos * (1.0 - pos_alpha) + raw_pos * pos_alpha

    raw_rot = _pose_rotation(raw_pose)
    anchor_rot = _pose_rotation(anchor_pose)
    q_anchor = anchor_rot.as_quat()
    q_raw = raw_rot.as_quat()
    if np.dot(q_anchor, q_raw) < 0:
        q_raw = -q_raw
    q = q_anchor * (1.0 - rot_alpha) + q_raw * rot_alpha
    q_norm = np.linalg.norm(q)
    if q_norm <= 1e-9:
        q = q_anchor
    else:
        q = q / q_norm
    qx, qy, qz, qw = q
    if qw < 0:
        qx, qy, qz, qw = -qx, -qy, -qz, -qw
    return {
        'x': float(pos[0]), 'y': float(pos[1]), 'z': float(pos[2]),
        'qx': float(qx), 'qy': float(qy), 'qz': float(qz), 'qw': float(qw),
    }


def _stabilize_pose(raw_pose: dict, anchor_pose: dict | None) -> tuple[dict, dict]:
    """Keep PnP precision near the matched keyframe and reject large jumps."""
    if anchor_pose is None:
        return raw_pose, {
            'anchorApplied': False,
            'positionDeltaM': None,
            'yawDeltaDeg': None,
            'positionAlpha': 1.0,
            'rotationAlpha': 1.0,
        }

    pos_delta = float(np.linalg.norm(_pose_position(raw_pose) - _pose_position(anchor_pose)))
    yaw_delta = _yaw_diff(
        _yaw_from_rotation(_pose_rotation(raw_pose)),
        _yaw_from_rotation(_pose_rotation(anchor_pose)),
    )
    yaw_delta_deg = math.degrees(yaw_delta)

    if pos_delta <= 0.75:
        pos_alpha = 0.85
    elif pos_delta <= 1.50:
        pos_alpha = 0.50
    elif pos_delta <= 2.50:
        pos_alpha = 0.25
    else:
        pos_alpha = 0.0

    if yaw_delta_deg <= 8.0:
        rot_alpha = 0.85
    elif yaw_delta_deg <= 18.0:
        rot_alpha = 0.50
    elif yaw_delta_deg <= 35.0:
        rot_alpha = 0.20
    else:
        rot_alpha = 0.0

    return _blend_pose(raw_pose, anchor_pose, pos_alpha, rot_alpha), {
        'anchorApplied': True,
        'positionDeltaM': pos_delta,
        'yawDeltaDeg': yaw_delta_deg,
        'positionAlpha': pos_alpha,
        'rotationAlpha': rot_alpha,
    }


def _pose_distance(a: dict, b: dict) -> float:
    return float(np.linalg.norm(_pose_position(a) - _pose_position(b)))


def _pose_yaw_distance_deg(a: dict, b: dict) -> float:
    return math.degrees(_yaw_diff(
        _yaw_from_rotation(_pose_rotation(a)),
        _yaw_from_rotation(_pose_rotation(b)),
    ))


def _average_cluster_pose(candidates: list[dict]) -> dict:
    positions = np.stack([_pose_position(c['stable_pose']) for c in candidates], axis=0)
    pos = np.median(positions, axis=0)

    quats = np.stack([
        np.array([
            c['stable_pose']['qx'], c['stable_pose']['qy'],
            c['stable_pose']['qz'], c['stable_pose']['qw'],
        ], dtype=np.float64)
        for c in candidates
    ], axis=0)
    ref = quats[0]
    for i in range(len(quats)):
        if np.dot(ref, quats[i]) < 0:
            quats[i] = -quats[i]
    q = np.mean(quats, axis=0)
    q_norm = np.linalg.norm(q)
    if q_norm <= 1e-9:
        q = ref
    else:
        q = q / q_norm
    qx, qy, qz, qw = q
    if qw < 0:
        qx, qy, qz, qw = -qx, -qy, -qz, -qw
    return {
        'x': float(pos[0]), 'y': float(pos[1]), 'z': float(pos[2]),
        'qx': float(qx), 'qy': float(qy), 'qz': float(qz), 'qw': float(qw),
    }


def _select_consensus(candidates: list[dict]) -> dict | None:
    if not candidates:
        return None
    ordered = sorted(candidates, key=lambda c: c['score'], reverse=True)
    clusters: list[list[dict]] = []

    for cand in ordered:
        for cluster in clusters:
            ref = cluster[0]
            if (
                _pose_distance(cand['stable_pose'], ref['stable_pose']) <= 1.50
                and _pose_yaw_distance_deg(cand['stable_pose'], ref['stable_pose']) <= 25.0
            ):
                cluster.append(cand)
                break
        else:
            clusters.append([cand])

    def cluster_key(cluster: list[dict]) -> tuple:
        image_votes = len({c['matched_image_index'] for c in cluster})
        return (
            image_votes,
            sum(c['score'] for c in cluster),
            sum(c['num_matches'] for c in cluster),
            -min(c['reprojection_error'] for c in cluster),
        )

    best_cluster = max(clusters, key=cluster_key)
    best = max(best_cluster, key=lambda c: c['score'])
    result = dict(best)
    if len(best_cluster) >= 2:
        result['pose'] = _average_cluster_pose(best_cluster)
        result['confidence'] = float(np.mean([c['confidence'] for c in best_cluster]))
        result['num_matches'] = int(max(c['num_matches'] for c in best_cluster))
        result['consensus_size'] = len({c['matched_image_index'] for c in best_cluster})
    else:
        result['pose'] = best['stable_pose']
        result['consensus_size'] = 1
    return result


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

        pose_candidates: list[dict] = []

        for img_idx, img_bytes in enumerate(images):
            gray = _to_gray_float(img_bytes)
            if gray is None:
                continue
            image_candidates: list[dict] = []

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

                inlier_idx = inliers.flatten().astype(np.int32)
                projected, _ = cv2.projectPoints(
                    pts_3d[inlier_idx], rvec, tvec, K, None
                )
                projected = projected.reshape(-1, 2)
                reproj_errors = np.linalg.norm(projected - pts_2d[inlier_idx], axis=1)
                reproj_error = float(np.median(reproj_errors))

                h, w = gray.shape[:2]
                inlier_2d = pts_2d[inlier_idx]
                span = np.maximum(inlier_2d.max(axis=0) - inlier_2d.min(axis=0), 0.0)
                coverage = float((span[0] / max(w, 1)) * (span[1] / max(h, 1)))

                # Convert PnP result to RTABMap world pose
                # (same convention as RTABMapEngine / map_manager)
                R_w2c, _ = cv2.Rodrigues(rvec)
                R_cw = R_w2c.T @ C
                t_cw = (-R_w2c.T @ tvec).flatten()

                qx, qy, qz, qw = _rotation_to_quat(R_cw)
                confidence = min(0.99, max(0.01, n_in / len(pts_3d)))
                raw_pose = {
                    'x': float(t_cw[0]), 'y': float(t_cw[1]), 'z': float(t_cw[2]),
                    'qx': qx, 'qy': qy, 'qz': qz, 'qw': qw,
                }
                anchor_matrix = loaded.keyframe_poses.get(node_id)
                anchor_pose = (
                    _matrix_pose_to_dict(anchor_matrix)
                    if anchor_matrix is not None
                    else None
                )
                stable_pose, anchor_debug = _stabilize_pose(raw_pose, anchor_pose)

                coverage_factor = min(1.0, max(0.25, coverage / 0.10))
                reproj_factor = 1.0 / max(1.0, reproj_error / 3.0)
                score = float(n_in * (0.5 + confidence) * coverage_factor * reproj_factor)

                candidate = {
                    'num_matches': n_in,
                    'confidence': confidence,
                    'matched_image_index': img_idx,
                    'pose': stable_pose,
                    'stable_pose': stable_pose,
                    'raw_pose': raw_pose,
                    'matched_node_id': int(node_id),
                    'score': score,
                    'reprojection_error': reproj_error,
                    'coverage': coverage,
                    'anchor_debug': anchor_debug,
                }
                image_candidates.append(candidate)

            if image_candidates:
                pose_candidates.append(max(image_candidates, key=lambda c: c['score']))

        best = _select_consensus(pose_candidates)
        if best is None:
            raise ValueError("SuperPoint+LightGlue: insufficient matches")

        logger.info(
            f"[SuperPoint] map={map_id} inliers={best['num_matches']} "
            f"confidence={best['confidence']:.3f} "
            f"consensus={best.get('consensus_size', 1)} "
            f"node={best.get('matched_node_id')}"
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
