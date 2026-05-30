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


def _resize_query_images(
    images: list[bytes], *, width: int, height: int
) -> list[bytes]:
    """Query 이미지를 인덱스 해상도(width x height)로 맞춘다.

    EXIF 회전 보정 후, 종횡비 방향(가로/세로)이 인덱스와 다르면 90도 회전한다.
    iOS ARKit 프레임은 EXIF orientation=1로 landscape(960x720) raw가 들어오므로
    EXIF 보정만으로는 portrait 인덱스(720x960)와 어긋나 resize 시 stretch되어
    SuperPoint 매칭이 전멸한다. 종횡비 기반 회전으로 이를 막는다.
    회전 방향은 시계방향 90도(ROTATE_270) — iOS 후면 카메라 raw 기준 정방향.
    회전은 매칭 목적의 in-plane(roll) 정렬이라 pose 광축 방향은 불변 → pose 역보정 불요.
    """
    resized = []
    for img_bytes in images:
        try:
            img = ImageOps.exif_transpose(Image.open(io.BytesIO(img_bytes)))
            if (img.width > img.height) != (width > height):
                img = img.transpose(Image.ROTATE_270)
            if img.size != (width, height):
                img = img.resize((width, height), Image.LANCZOS)
            buf = io.BytesIO()
            img.save(buf, format="JPEG", quality=95)
            resized.append(buf.getvalue())
        except Exception as exc:
            raise ValueError(f"Invalid image data: {exc}") from exc
    return resized
