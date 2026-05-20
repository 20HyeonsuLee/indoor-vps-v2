"""SuperPoint + LightGlue localization engine."""
import asyncio
import io
import logging
import os
import time

import cv2
import numpy as np
import torch
from PIL import Image, ImageOps
from scipy.spatial.transform import Rotation

logger = logging.getLogger(__name__)

# RGBD(3D-3D) path는 ARKit LiDAR depth의 hallucination/복도 천공으로 인해
# 짧은 view(가까운 KF)에 conf가 비대해지는 편향이 있어 기본 비활성.
# 켜고 싶을 때만 SLAM_USE_RGBD=true.
_USE_RGBD = os.environ.get("SLAM_USE_RGBD", "false").strip().lower() in ("1", "true", "yes")
_EARLY_CONFIDENCE = float(os.environ.get("SLAM_EARLY_CONFIDENCE", "0.75"))
_EARLY_MIN_MATCHES = int(os.environ.get("SLAM_EARLY_MIN_MATCHES", "10"))


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


def _horn_align(p_src: np.ndarray, p_dst: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """SVD-based rigid alignment: R, t such that R @ p_src.T + t ≈ p_dst.T."""
    src_c = p_src.mean(axis=0)
    dst_c = p_dst.mean(axis=0)
    H = (p_src - src_c).T @ (p_dst - dst_c)
    U, _, Vt = np.linalg.svd(H)
    d = float(np.sign(np.linalg.det(Vt.T @ U.T)))
    D = np.diag([1.0, 1.0, d])
    R = Vt.T @ D @ U.T
    t = dst_c - R @ src_c
    return R, t


def _ransac_3d3d(
    p_src: np.ndarray,
    p_dst: np.ndarray,
    threshold: float = 0.10,
    iterations: int = 500,
    min_final_inliers: int = 6,
):
    """RANSAC over Horn rigid alignment. Returns (R, t, inlier_idx) or None."""
    n = len(p_src)
    if n < 3:
        return None
    rng = np.random.default_rng(0)
    best_inliers = np.array([], dtype=np.int64)
    for _ in range(iterations):
        idx = rng.choice(n, 3, replace=False)
        try:
            R, t = _horn_align(p_src[idx], p_dst[idx])
        except np.linalg.LinAlgError:
            continue
        pred = p_src @ R.T + t
        err = np.linalg.norm(pred - p_dst, axis=1)
        inl = np.where(err < threshold)[0]
        if len(inl) > len(best_inliers):
            best_inliers = inl
    if len(best_inliers) < min_final_inliers:
        return None
    R, t = _horn_align(p_src[best_inliers], p_dst[best_inliers])
    return R, t, best_inliers


class SuperPointEngine:
    """Localization engine using SuperPoint + LightGlue."""

    def __init__(self):
        from lightglue import LightGlue, SuperPoint
        from indoor_server.application.slam.superpoint.device import resolve_torch_device

        # cuDNN을 결정적 모드로. inference 한정, 같은 input → bit-exact 같은 output.
        # 인덱스 빌드 시점과 query 시점이 다른 process여도 SP feature 위치/descriptor
        # 동일성 보장. 속도 ~10-30% 손실은 SP forward 한 번 수십 ms라 무시 수준.
        torch.backends.cudnn.deterministic = True
        torch.backends.cudnn.benchmark = False

        self._device = resolve_torch_device()
        self._extractor = SuperPoint(max_num_keypoints=1024).eval().to(self._device)
        self._matcher = LightGlue(features='superpoint').eval().to(self._device)
        logger.info(f"[SuperPoint] Engine ready on {self._device} (cudnn.deterministic=True)")

    def _sync_device(self) -> None:
        if str(self._device).startswith("cuda"):
            torch.cuda.synchronize(self._device)

    def _elapsed_ms(self, started_at: float) -> float:
        self._sync_device()
        return (time.perf_counter() - started_at) * 1000.0

    def _good_enough(self, candidate: dict) -> bool:
        return (
            float(candidate["confidence"]) >= _EARLY_CONFIDENCE
            and int(candidate["num_matches"]) >= _EARLY_MIN_MATCHES
        )

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
        depths: list[bytes | None] | None = None,
    ) -> dict:
        from .map_manager import SuperPointMapManager, decode_depth_meters, lift_kps_camera_frame

        if intrinsics is None:
            raise ValueError("intrinsics required for SuperPoint localization")

        K = np.array([
            [intrinsics['fx'], 0,              intrinsics['cx']],
            [0,               intrinsics['fy'], intrinsics['cy']],
            [0,               0,               1              ],
        ], dtype=np.float64)
        image_w = int(intrinsics['width'])
        image_h = int(intrinsics['height'])

        mgr = SuperPointMapManager()
        loaded = mgr.get_or_load(map_id, db_path)

        if not loaded.node_ids:
            raise ValueError("No keyframes with stored images in this map")

        # RTABMap camera-frame → OpenCV optical-frame conversion matrix.
        # rtabmap-cam (ROS body): x=forward, y=left, z=up
        # opencv-cam (optical):   x=right,   y=down, z=forward
        # v_opencv = C @ v_rtabmap-cam
        #   x_fwd(rtabmap) -> z_fwd(opencv)        -> column [0, 0, 1]
        #   y_left(rtabmap) -> -x_right(opencv)    -> column [-1, 0, 0]
        #   z_up(rtabmap)   -> -y_down(opencv)     -> column [0, -1, 0]
        C = np.array([[0, -1, 0], [0, 0, -1], [1, 0, 0]], dtype=np.float64)

        best: dict | None = None
        localize_started_at = time.perf_counter()

        for img_idx, img_bytes in enumerate(images):
            image_started_at = time.perf_counter()
            stage_started_at = time.perf_counter()
            gray = _to_gray_float(img_bytes)
            if gray is None:
                continue
            decode_ms = self._elapsed_ms(stage_started_at)

            stage_started_at = time.perf_counter()
            q_feats = self._extract(gray)
            q_kps = q_feats['keypoints'][0].cpu().numpy()  # (N, 2)
            q_feats_cpu = {k: v.cpu() for k, v in q_feats.items()}
            superpoint_ms = self._elapsed_ms(stage_started_at)

            # If depth is provided for this frame, pre-lift query SP kp to rtab-cam 3D
            # for the RGBD (3D-3D) path. None → 2D-3D PnP fallback.
            q_rtab_3d = None
            if depths is not None and img_idx < len(depths) and depths[img_idx]:
                blob_size = len(depths[img_idx])
                depth_m = decode_depth_meters(depths[img_idx])
                if depth_m is not None:
                    q_rtab_3d = lift_kps_camera_frame(
                        q_kps, depth_m, K, image_w, image_h
                    )
                    n_valid = int(np.sum(~np.isnan(q_rtab_3d).any(axis=1))) if q_rtab_3d is not None else 0
                    finite = depth_m[np.isfinite(depth_m)]
                    d_stats = (
                        f"depth_m_range[{finite.min():.2f},{finite.max():.2f}] "
                        f"depth_m_median={float(np.median(finite)):.2f}"
                        if finite.size else "depth_all_nan"
                    )
                    logger.warning(
                        f"[RGBD-debug] img_idx={img_idx} depth_blob={blob_size}B "
                        f"depth_shape={depth_m.shape} {d_stats} "
                        f"kp={len(q_kps)} q_rtab_3d_valid={n_valid}"
                    )
                else:
                    logger.warning(
                        f"[RGBD-debug] img_idx={img_idx} depth_blob={blob_size}B "
                        f"decode_depth_meters returned None — depth format mismatch"
                    )

            from .global_descriptor import GlobalDescExtractor
            stage_started_at = time.perf_counter()
            gray_uint8 = (gray * 255).clip(0, 255).astype(np.uint8)
            q_global = GlobalDescExtractor(self._device).extract(gray_uint8)  # (384,)
            dino_ms = self._elapsed_ms(stage_started_at)

            stage_started_at = time.perf_counter()
            candidate_scores = loaded.top_k_candidate_scores(q_global)
            candidates = [node_id for node_id, _ in candidate_scores]
            candidate_ms = self._elapsed_ms(stage_started_at)
            logger.info(
                "[SuperPointTrace] map=%s img=%d decodeMs=%.1f superpointMs=%.1f "
                "dinoMs=%.1f candidateMs=%.1f candidates=%s",
                map_id,
                img_idx,
                decode_ms,
                superpoint_ms,
                dino_ms,
                candidate_ms,
                ",".join(f"{node_id}:{score:.3f}" for node_id, score in candidate_scores),
            )

            for node_id in candidates:
                candidate_started_at = time.perf_counter()
                db_feats = loaded.keyframe_feats[node_id]
                world3d = loaded.keyframe_world3d[node_id]          # (M, 3)

                stage_started_at = time.perf_counter()
                matches = self._match(
                    q_feats_cpu,
                    db_feats,
                )  # (P, 2)
                match_ms = self._elapsed_ms(stage_started_at)

                if len(matches) < 4:
                    logger.info(
                        "[SuperPointTrace] map=%s img=%d node=%s lightglueMs=%.1f "
                        "matches=%d skip=too_few_matches",
                        map_id,
                        img_idx,
                        node_id,
                        match_ms,
                        len(matches),
                    )
                    continue

                # Compute keyframe centroid (avg world3d ≠ NaN) for diagnostic.
                valid_w_mask = ~np.isnan(world3d).any(axis=1)
                w_centroid = (
                    world3d[valid_w_mask].mean(axis=0)
                    if int(valid_w_mask.sum()) > 0
                    else np.array([float('nan')] * 3)
                )
                # --- RGBD path: 3D-3D rigid alignment when query depth available ---
                if _USE_RGBD and q_rtab_3d is not None:
                    rgbd = self._rgbd_estimate(matches, q_rtab_3d, world3d)
                    if rgbd is None:
                        # Pre-check what made it fail
                        valid_q = ~np.isnan(q_rtab_3d).any(axis=1)
                        valid_w = ~np.isnan(world3d).any(axis=1)
                        pair_ok = 0
                        for qi, di in matches:
                            if valid_q[qi] and valid_w[di]:
                                pair_ok += 1
                        logger.warning(
                            f"[RGBD-debug] img_idx={img_idx} node={node_id} "
                            f"matches={len(matches)} valid_pairs={pair_ok} "
                            f"→ rgbd_estimate=None, fallback to PnP"
                        )
                    if rgbd is not None:
                        n_in, R_cw, t_cw, n_pairs = rgbd
                        # confidence = inliers / valid 3D-3D pairs. PnP 와 같은
                        # denominator(매칭 후 3D 유효 쌍) 기준이라 직접 비교 가능.
                        confidence = min(0.99, max(0.01, n_in / max(n_pairs, 1)))
                        qx, qy, qz, qw = _rotation_to_quat(R_cw)
                        logger.warning(
                            f"[RGBD-debug] WIN node={node_id} kf_centroid=("
                            f"{w_centroid[0]:.2f},{w_centroid[1]:.2f},{w_centroid[2]:.2f}) "
                            f"→ pose t=({t_cw[0]:.2f},{t_cw[1]:.2f},{t_cw[2]:.2f}) "
                            f"inliers={n_in}/{n_pairs} conf={confidence:.2f}"
                        )
                        candidate = {
                            'num_matches': n_in,
                            'confidence': confidence,
                            'matched_image_index': img_idx,
                            'method_used': 'RGBD',
                            'pose': {
                                'x': float(t_cw[0]), 'y': float(t_cw[1]), 'z': float(t_cw[2]),
                                'qx': qx, 'qy': qy, 'qz': qz, 'qw': qw,
                            },
                        }
                        # RGBD 는 PnP 보다 항상 우선 — 단 confidence ≥ 0.3 일 때만.
                        # 그 미만이면 unreliable → PnP fallback 허용.
                        prev_is_rgbd = best is not None and best.get('method_used') == 'RGBD'
                        rgbd_reliable = confidence >= 0.30
                        if rgbd_reliable and (
                            best is None
                            or not prev_is_rgbd
                            or n_in > best['num_matches']
                        ):
                            best = candidate
                        continue
                    # RGBD failed (too few valid pairs or RANSAC underflow) → fall back to PnP.

                pts_2d, pts_3d = [], []
                for qi, di in matches:
                    w = world3d[di]
                    if np.any(np.isnan(w)):
                        continue
                    pts_2d.append(q_kps[qi])
                    pts_3d.append(w)

                if len(pts_3d) < 4:
                    logger.info(
                        "[SuperPointTrace] map=%s img=%d node=%s lightglueMs=%.1f "
                        "matches=%d valid3d=%d skip=too_few_3d",
                        map_id,
                        img_idx,
                        node_id,
                        match_ms,
                        len(matches),
                        len(pts_3d),
                    )
                    continue

                pts_2d = np.array(pts_2d, dtype=np.float64)
                pts_3d = np.array(pts_3d, dtype=np.float64)

                stage_started_at = time.perf_counter()
                ok, rvec, tvec, inliers = cv2.solvePnPRansac(
                    pts_3d, pts_2d, K, None,
                    flags=cv2.SOLVEPNP_EPNP,
                    reprojectionError=8.0,
                    confidence=0.99,
                    iterationsCount=1000,
                )

                if not ok or inliers is None or len(inliers) < 8:
                    logger.info(
                        "[SuperPointTrace] map=%s img=%d node=%s lightglueMs=%.1f "
                        "poseMs=%.1f matches=%d valid3d=%d skip=pnp_failed",
                        map_id,
                        img_idx,
                        node_id,
                        match_ms,
                        self._elapsed_ms(stage_started_at),
                        len(matches),
                        len(pts_3d),
                    )
                    continue

                n_in = len(inliers)
                _conf_local = n_in / max(len(pts_3d), 1)
                if _conf_local < 0.30:
                    logger.info(
                        "[SuperPointTrace] map=%s img=%d node=%s lightglueMs=%.1f "
                        "poseMs=%.1f matches=%d valid3d=%d inliers=%d confidence=%.3f "
                        "skip=low_confidence",
                        map_id,
                        img_idx,
                        node_id,
                        match_ms,
                        self._elapsed_ms(stage_started_at),
                        len(matches),
                        len(pts_3d),
                        n_in,
                        _conf_local,
                    )
                    continue

                # RANSAC EPNP는 algebraic minimum이라 sub-pixel 정확도 부족. inlier
                # 셋에 대해 Levenberg-Marquardt iterative refinement 적용해 reprojection
                # error를 픽셀 단위 → sub-pixel로 좁힘.
                inlier_idx = inliers.ravel()
                pts_3d_in = pts_3d[inlier_idx]
                pts_2d_in = pts_2d[inlier_idx]
                rvec, tvec = cv2.solvePnPRefineLM(
                    pts_3d_in, pts_2d_in, K, None, rvec, tvec,
                    criteria=(cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_COUNT, 50, 1e-6),
                )
                pose_ms = self._elapsed_ms(stage_started_at)

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
                    'method_used': 'PnP',
                    'pose': {
                        'x': float(t_cw[0]), 'y': float(t_cw[1]), 'z': float(t_cw[2]),
                        'qx': qx, 'qy': qy, 'qz': qz, 'qw': qw,
                    },
                }
                # 이미 RGBD 가 best 면 PnP 후보는 무시 (RGBD 우선).
                if best is not None and best.get('method_used') == 'RGBD':
                    continue
                if best is None or n_in > best['num_matches']:
                    best = candidate
                logger.info(
                    "[SuperPointTrace] map=%s img=%d node=%s lightglueMs=%.1f "
                    "poseMs=%.1f totalCandidateMs=%.1f method=PnP matches=%d "
                    "valid3d=%d inliers=%d confidence=%.3f early=%s",
                    map_id,
                    img_idx,
                    node_id,
                    match_ms,
                    pose_ms,
                    self._elapsed_ms(candidate_started_at),
                    len(matches),
                    len(pts_3d),
                    n_in,
                    confidence,
                    self._good_enough(candidate),
                )
                if self._good_enough(candidate):
                    logger.info(
                        "[SuperPoint] early-stop map=%s img=%d node=%s inliers=%d "
                        "confidence=%.3f method=PnP imageMs=%.1f totalMs=%.1f",
                        map_id,
                        img_idx,
                        node_id,
                        n_in,
                        confidence,
                        self._elapsed_ms(image_started_at),
                        self._elapsed_ms(localize_started_at),
                    )
                    return {**candidate, 'map_id': map_id, 'method': 'SuperPoint+LightGlue'}

            logger.info(
                "[SuperPointTrace] map=%s img=%d imageTotalMs=%.1f bestConfidence=%.3f bestMatches=%d",
                map_id,
                img_idx,
                self._elapsed_ms(image_started_at),
                float(best["confidence"]) if best is not None else 0.0,
                int(best["num_matches"]) if best is not None else 0,
            )

        if best is None:
            raise ValueError("SuperPoint+LightGlue: insufficient matches")

        logger.info(
            f"[SuperPoint] map={map_id} inliers={best['num_matches']} "
            f"confidence={best['confidence']:.3f} method={best.get('method_used')}"
        )
        return {**best, 'map_id': map_id, 'method': 'SuperPoint+LightGlue'}

    def _rgbd_estimate(
        self,
        matches: np.ndarray,           # (P, 2) [qi, di]
        q_rtab_3d: np.ndarray,         # (N, 3) query SP kp in rtab-cam frame, NaN if no depth
        world3d: np.ndarray,           # (M, 3) DB world 3D, NaN if no depth
    ):
        """3D-3D Horn RANSAC. Returns (n_inliers, R_cam→world, t, n_valid_pairs) or None.

        Result R, t directly equals rtabmap-stored "T_world_camera" convention.
        n_valid_pairs = 3D-3D pair 후보 수 (denominator for confidence).
        """
        pts_q, pts_w = [], []
        for qi, di in matches:
            q = q_rtab_3d[qi]
            w = world3d[di]
            if np.any(np.isnan(q)) or np.any(np.isnan(w)):
                continue
            pts_q.append(q)
            pts_w.append(w)
        if len(pts_q) < 6:
            logger.warning(f"[RGBD-debug] _rgbd_estimate: valid pairs {len(pts_q)} < 6")
            return None
        pts_q = np.asarray(pts_q, dtype=np.float64)
        pts_w = np.asarray(pts_w, dtype=np.float64)
        # Range diagnostic — depth in m should typically be 0.1~30. world3d in
        # rtabmap world coords spans a few~tens of meters.
        logger.warning(
            f"[RGBD-debug] _rgbd_estimate: pairs={len(pts_q)} "
            f"q_x[{pts_q[:,0].min():.2f},{pts_q[:,0].max():.2f}] "
            f"q_y[{pts_q[:,1].min():.2f},{pts_q[:,1].max():.2f}] "
            f"q_z[{pts_q[:,2].min():.2f},{pts_q[:,2].max():.2f}] "
            f"w_x[{pts_w[:,0].min():.2f},{pts_w[:,0].max():.2f}] "
            f"w_y[{pts_w[:,1].min():.2f},{pts_w[:,1].max():.2f}] "
            f"w_z[{pts_w[:,2].min():.2f},{pts_w[:,2].max():.2f}]"
        )
        # LiDAR depth(±10cm 노이즈) + SP feature 위치 오차 + world3d 누적 오차로
        # 실측 데이터에서 매칭당 3D 잔차가 30~50cm까지 흔함. 0.50m로 완화.
        # iterations 늘려 cross-image 노이지 pair set 에서도 inlier 셋 찾을 확률 향상.
        result = _ransac_3d3d(pts_q, pts_w, threshold=0.50, iterations=2000, min_final_inliers=6)
        if result is None:
            logger.warning(
                f"[RGBD-debug] _ransac_3d3d returned None — "
                f"either no inlier set >= 6 or RANSAC could not find consistent transform"
            )
            return None
        R, t, inliers = result
        ratio = len(inliers) / max(len(pts_q), 1)
        # SP 매칭은 PnP와 달리 3D-3D Horn 검증에서 false positive 매칭 비율이
        # 높음(매칭 정밀도 ~수픽셀, 3D 노이즈 누적). 실측 환경에서 절반 이상이
        # outlier로 reject되는 게 정상이라 10% 정도면 transform 신뢰 가능.
        if ratio < 0.10:
            logger.warning(
                f"[RGBD-debug] inlier ratio {ratio:.2%} < 10% (inliers={len(inliers)} / pairs={len(pts_q)})"
            )
            return None
        return len(inliers), R, t, len(pts_q)

    async def localize(
        self,
        map_id: str,
        images: list[bytes],
        intrinsics: dict | None = None,
        initial_pose: dict | None = None,
        db_path: str | None = None,
        depths: list[bytes | None] | None = None,
        **kwargs,
    ) -> dict:
        return await asyncio.to_thread(self._localize_sync, map_id, images, intrinsics, db_path, depths)
