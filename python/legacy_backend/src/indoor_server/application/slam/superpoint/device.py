from __future__ import annotations

import os

import torch


def requested_torch_device_name() -> str:
    return (
        os.environ.get("INDOOR_ML_DEVICE")
        or os.environ.get("PYTHON_ML_DEVICE")
        or "cpu"
    ).strip().lower() or "cpu"


def resolve_torch_device() -> torch.device:
    requested = requested_torch_device_name()
    if requested == "cpu":
        return torch.device("cpu")
    if requested == "cuda" or requested.startswith("cuda:"):
        if not torch.cuda.is_available():
            raise RuntimeError("cuda requested but torch.cuda.is_available() is false")
        return torch.device(requested)
    raise RuntimeError(f"unsupported ML device: {requested}")
