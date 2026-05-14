from __future__ import annotations

import struct

from indoor_server.domain.building.rtabmap_models import Matrix4x4


def decode_pose_3x4_blob(blob: bytes) -> Matrix4x4:
    if len(blob) != 48:
        raise ValueError(f"RTAB-Map pose blob must be 48 bytes, got {len(blob)}")
    values = struct.unpack("<12f", blob)
    return (
        (float(values[0]), float(values[1]), float(values[2]), float(values[3])),
        (float(values[4]), float(values[5]), float(values[6]), float(values[7])),
        (float(values[8]), float(values[9]), float(values[10]), float(values[11])),
        (0.0, 0.0, 0.0, 1.0),
    )
