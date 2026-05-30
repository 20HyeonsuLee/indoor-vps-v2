from __future__ import annotations

import os

import torch


def resolve_torch_device() -> torch.device:
    requested = os.environ.get("INDOOR_ML_DEVICE", "cpu").strip().lower() or "cpu"
    if requested == "cpu":
        return torch.device("cpu")
    if requested == "cuda" or requested.startswith("cuda:"):
        if not torch.cuda.is_available():
            raise RuntimeError("INDOOR_ML_DEVICE=cuda but torch.cuda.is_available() is false")
        return torch.device(requested)
    if requested == "mps":
        mps_backend = getattr(torch.backends, "mps", None)
        if mps_backend is None or not mps_backend.is_available():
            raise RuntimeError("INDOOR_ML_DEVICE=mps but torch.backends.mps.is_available() is false")
        return torch.device("mps")
    raise RuntimeError(f"unsupported INDOOR_ML_DEVICE: {requested}")
