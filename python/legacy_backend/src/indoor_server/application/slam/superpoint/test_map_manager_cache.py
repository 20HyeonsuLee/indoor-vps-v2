"""Unit tests: SuperPointLoadedMap disk cache round-trip.

Tests _save_cache / _try_load_cache without invoking _build_index
(no torch/lightglue/CUDA required).
"""
import json
import os
import time
from pathlib import Path
from unittest.mock import patch

import numpy as np
import pytest
import torch


def _make_loaded_map_without_build(
    tmp_path: Path,
    db_path: Path,
    cache_dir: Path,
) -> "SuperPointLoadedMap":
    """Construct SuperPointLoadedMap, bypassing _build_index via mock."""
    from indoor_server.application.slam.superpoint.map_manager import SuperPointLoadedMap

    with patch.object(SuperPointLoadedMap, "_build_index"):
        with patch.object(SuperPointLoadedMap, "_save_cache"):
            obj = SuperPointLoadedMap.__new__(SuperPointLoadedMap)
            obj.map_id = "test_map"
            obj.db_path = str(db_path)
            obj.device = torch.device("cpu")
            obj._cache_dir = cache_dir
            obj.node_ids = []
            obj.keyframe_feats = {}
            obj.keyframe_world3d = {}
            obj.global_descs = None
    return obj


def _populate_map(obj, frame_ids: list[int], n_kp: int = 8) -> None:
    """Fill obj with synthetic per-frame data."""
    obj.node_ids = list(frame_ids)
    rng = np.random.default_rng(42)
    for nid in frame_ids:
        kps = rng.random((n_kp, 2), dtype=np.float32)
        descs = rng.random((n_kp, 256), dtype=np.float32)
        w3d = rng.random((n_kp, 3), dtype=np.float32)
        obj.keyframe_feats[nid] = {
            "keypoints": torch.from_numpy(kps).unsqueeze(0),
            "descriptors": torch.from_numpy(descs).unsqueeze(0),
            "image_size": torch.tensor([[640, 480]]),
        }
        obj.keyframe_world3d[nid] = w3d
    global_arr = rng.random((len(frame_ids), 384), dtype=np.float32)
    obj.global_descs = torch.from_numpy(global_arr)


class TestSuperpointCacheRoundTrip:
    def test_save_then_load_restores_shape(self, tmp_path):
        db_path = tmp_path / "rtabmap.db"
        db_path.write_bytes(b"dummy")
        cache_dir = tmp_path / "superpoint_index"

        obj = _make_loaded_map_without_build(tmp_path, db_path, cache_dir)
        frame_ids = [1, 2, 3]
        _populate_map(obj, frame_ids, n_kp=10)
        obj._save_cache()

        assert (cache_dir / "keypoints.npy").exists()
        assert (cache_dir / "descriptors.npy").exists()
        assert (cache_dir / "frame_offsets.npy").exists()
        assert (cache_dir / "world_points.npy").exists()
        assert (cache_dir / "global_descriptors.npy").exists()
        assert (cache_dir / "meta.json").exists()

        loader = _make_loaded_map_without_build(tmp_path, db_path, cache_dir)
        result = loader._try_load_cache()
        assert result is True

        assert loader.node_ids == frame_ids
        for nid in frame_ids:
            assert loader.keyframe_feats[nid]["keypoints"].shape == (1, 10, 2)
            assert loader.keyframe_feats[nid]["descriptors"].shape == (1, 10, 256)
            assert loader.keyframe_world3d[nid].shape == (10, 3)
        assert loader.global_descs is not None
        assert loader.global_descs.shape == (3, 384)

    def test_save_then_load_preserves_values(self, tmp_path):
        db_path = tmp_path / "rtabmap.db"
        db_path.write_bytes(b"dummy")
        cache_dir = tmp_path / "superpoint_index"

        obj = _make_loaded_map_without_build(tmp_path, db_path, cache_dir)
        _populate_map(obj, [10, 20], n_kp=5)
        orig_kps = obj.keyframe_feats[10]["keypoints"][0].numpy().copy()
        orig_w3d = obj.keyframe_world3d[20].copy()
        obj._save_cache()

        loader = _make_loaded_map_without_build(tmp_path, db_path, cache_dir)
        loader._try_load_cache()

        np.testing.assert_allclose(
            loader.keyframe_feats[10]["keypoints"][0].numpy(), orig_kps, rtol=1e-5
        )
        np.testing.assert_allclose(
            loader.keyframe_world3d[20], orig_w3d, rtol=1e-5
        )

    def test_stale_mtime_triggers_rebuild(self, tmp_path):
        db_path = tmp_path / "rtabmap.db"
        db_path.write_bytes(b"dummy")
        cache_dir = tmp_path / "superpoint_index"

        obj = _make_loaded_map_without_build(tmp_path, db_path, cache_dir)
        _populate_map(obj, [1])
        obj._save_cache()

        # Overwrite meta with wrong mtime
        meta_path = cache_dir / "meta.json"
        meta = json.loads(meta_path.read_text())
        meta["db_mtime"] = 0.0
        meta_path.write_text(json.dumps(meta))

        loader = _make_loaded_map_without_build(tmp_path, db_path, cache_dir)
        result = loader._try_load_cache()
        assert result is False

    def test_missing_npy_triggers_rebuild(self, tmp_path):
        db_path = tmp_path / "rtabmap.db"
        db_path.write_bytes(b"dummy")
        cache_dir = tmp_path / "superpoint_index"

        obj = _make_loaded_map_without_build(tmp_path, db_path, cache_dir)
        _populate_map(obj, [1])
        obj._save_cache()

        (cache_dir / "descriptors.npy").unlink()

        loader = _make_loaded_map_without_build(tmp_path, db_path, cache_dir)
        result = loader._try_load_cache()
        assert result is False

    def test_no_cache_dir_returns_false(self, tmp_path):
        db_path = tmp_path / "rtabmap.db"
        db_path.write_bytes(b"dummy")

        obj = _make_loaded_map_without_build(tmp_path, db_path, cache_dir=None)
        assert obj._try_load_cache() is False

    def test_empty_map_save_is_noop(self, tmp_path):
        db_path = tmp_path / "rtabmap.db"
        db_path.write_bytes(b"dummy")
        cache_dir = tmp_path / "superpoint_index"

        obj = _make_loaded_map_without_build(tmp_path, db_path, cache_dir)
        # node_ids is empty — _save_cache should skip
        obj._save_cache()
        assert not cache_dir.exists()
