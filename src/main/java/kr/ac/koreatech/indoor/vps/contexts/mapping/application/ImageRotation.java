package kr.ac.koreatech.indoor.vps.contexts.mapping.application;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Query image/depth를 90° CW 회전. SuperPoint shadow path에서만 사용 (rtabmap
 * iOS native db가 portrait로 저장하는데 우리 클라는 landscape query만 보내서
 * keyframe ↔ query orientation mismatch가 생김 → SuperPoint feature 매칭 ~0).
 *
 * <p>실패 시 원본 그대로 반환 (조용한 fallback). depth PNG는 16-bit grayscale도
 * BufferedImage로 로드해 회전 가능.
 */
final class ImageRotation {
    private ImageRotation() {
    }

    /** B는 portrait keyframe. 이미 portrait이면 회전 안 함 (그렇지 않으면 다시 landscape로 망가짐). */
    static byte[] ensurePortrait90Cw(byte[] payload, String format) {
        if (payload == null || payload.length == 0) {
            return payload;
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(payload));
            if (src == null) {
                return payload;
            }
            int w = src.getWidth();
            int h = src.getHeight();
            if (w <= h) {
                // 이미 portrait — 그대로 통과
                return payload;
            }
            BufferedImage dst = new BufferedImage(h, w, src.getType());
            Graphics2D g = dst.createGraphics();
            g.translate(h, 0);
            g.rotate(Math.PI / 2);
            g.drawImage(src, 0, 0, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length);
            ImageIO.write(dst, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            return payload;
        }
    }
}
