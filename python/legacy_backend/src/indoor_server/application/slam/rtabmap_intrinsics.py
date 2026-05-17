"""RTAB-Map intrinsics reader for image localization."""

from __future__ import annotations

import sqlite3
import struct
from pathlib import Path

_CAMERA_MODEL_HEADER_SIZE = 11
_MONO_CAMERA_TYPE = 0


class RTABMapEngine:
    def extract_intrinsics_from_db(self, db_path: str) -> dict:
        path = Path(db_path)
        if not path.exists():
            raise FileNotFoundError(f"Database file not found: {db_path}")

        with sqlite3.connect(path) as conn:
            row = conn.execute(
                """
                SELECT calibration
                FROM Data
                WHERE calibration IS NOT NULL
                LIMIT 1
                """
            ).fetchone()

        if not row or row[0] is None:
            raise ValueError("RTABMap Data.calibration not found")

        calibration = _decode_calibration(bytes(row[0]))
        width, height = calibration["image_size"]
        k = calibration["k"]
        fx, fy, cx, cy = float(k[0]), float(k[4]), float(k[2]), float(k[5])
        if fx <= 0 or fy <= 0 or width <= 0 or height <= 0:
            raise ValueError("invalid RTABMap camera intrinsics")
        return {
            "fx": fx,
            "fy": fy,
            "cx": cx,
            "cy": cy,
            "width": int(width),
            "height": int(height),
        }

def _decode_calibration(data: bytes) -> dict:
    header_bytes = _CAMERA_MODEL_HEADER_SIZE * 4
    if len(data) < header_bytes:
        raise ValueError("calibration header too short")

    header = struct.unpack("<11i", data[:header_bytes])
    if header[3] != _MONO_CAMERA_TYPE:
        raise ValueError(f"unsupported camera type: {header[3]}")

    k_count = header[6]
    d_count = header[7]
    r_count = header[8]
    p_count = header[9]
    local_count = header[10]
    if k_count != 9:
        raise ValueError(f"unexpected K count: {k_count}")

    required = header_bytes + 8 * (k_count + d_count + r_count + p_count) + 4 * local_count
    if len(data) < required:
        raise ValueError(f"calibration data too short: {len(data)} < {required}")

    offset = header_bytes
    k = struct.unpack("<9d", data[offset : offset + 72])
    return {
        "version": (int(header[0]), int(header[1]), int(header[2])),
        "image_size": (int(header[4]), int(header[5])),
        "k": k,
        "bytes_read": required,
    }
