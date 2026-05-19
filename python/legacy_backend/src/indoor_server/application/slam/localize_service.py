from __future__ import annotations

import io
import threading
from typing import TYPE_CHECKING

from PIL import Image, ImageOps

if TYPE_CHECKING:
    from indoor_server.application.slam.superpoint.engine import SuperPointEngine

_sp_engine = None
_sp_engine_lock = threading.Lock()


def _get_sp_engine() -> SuperPointEngine:
    from indoor_server.application.slam.superpoint.engine import SuperPointEngine

    global _sp_engine
    if _sp_engine is None:
        with _sp_engine_lock:
            if _sp_engine is None:
                _sp_engine = SuperPointEngine()
    return _sp_engine


def _resize_query_images(images: list[bytes], *, width: int, height: int) -> list[bytes]:
    """Resize query images to the SuperPoint index's expected resolution.

    크기가 이미 일치하고 EXIF 회전도 필요 없으면 원본 bytes를 그대로 반환.
    JPEG 재인코딩(quality=95)은 lossy → SuperPoint feature 위치/descriptor가
    매핑 시점과 미세하게 어긋나 self-localize에서도 cm급 오차의 원인.
    """
    resized = []
    for img_bytes in images:
        try:
            img = Image.open(io.BytesIO(img_bytes))
            needs_orient = (img.getexif().get(0x0112, 1) != 1)
            if img.size == (width, height) and not needs_orient:
                resized.append(img_bytes)
                continue
            img = ImageOps.exif_transpose(img)
            if img.size != (width, height):
                img = img.resize((width, height), Image.LANCZOS)
            buf = io.BytesIO()
            img.save(buf, format="JPEG", quality=95)
            resized.append(buf.getvalue())
        except Exception as exc:
            raise ValueError(f"Invalid image data: {exc}") from exc
    return resized
