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
    raise RuntimeError(f"unsupported INDOOR_ML_DEVICE: {requested}")
