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
    resized = []
    for img_bytes in images:
        try:
            img = ImageOps.exif_transpose(Image.open(io.BytesIO(img_bytes)))
            if img.size != (width, height):
                img = img.resize((width, height), Image.LANCZOS)
            buf = io.BytesIO()
            img.save(buf, format="JPEG", quality=95)
            resized.append(buf.getvalue())
        except Exception as exc:
            raise ValueError(f"Invalid image data: {exc}") from exc
    return resized
