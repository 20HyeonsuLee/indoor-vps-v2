package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferUShort;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ARKit LiDAR 신뢰 거리(약 5m) 너머의 depth 픽셀은 ARKit이 ML로 hallucinate한
 * 값일 가능성이 매우 큼. 클라가 보낸 depth/confidence PNG를 받아 그런 픽셀의
 * depth와 confidence를 모두 0으로 마스킹해서 다시 PNG로 인코딩.
 *
 * <p>마스킹 후:
 * - rtabmap이 depth=0 픽셀을 자동 skip → SLAM keypoint, cloud export 모두 깨끗
 * - confidence=0은 메타 일관성 (rtabmap-export --depth_confidence 사용 시 의미)
 */
public final class DepthRangeMasker {
    private static final Logger log = LoggerFactory.getLogger(DepthRangeMasker.class);

    private DepthRangeMasker() {
    }

    public record Result(byte[] depth, byte[] confidence) {
    }

    /**
     * @param depthPng 16-bit grayscale PNG (rtabmap 표준 depth blob). null이면 마스킹 없음.
     * @param confPng 8-bit grayscale PNG (ARKit confidenceMap 매핑). null 허용.
     * @param maxRangeMm 이 값 초과 depth는 0으로 마킹.
     */
    public static Result mask(byte[] depthPng, byte[] confPng, int maxRangeMm) {
        if (depthPng == null) {
            return new Result(null, confPng);
        }
        try {
            BufferedImage depthImg = ImageIO.read(new ByteArrayInputStream(depthPng));
            if (depthImg == null) {
                return new Result(depthPng, confPng);
            }
            int type = depthImg.getType();
            if (type == BufferedImage.TYPE_USHORT_GRAY) {
                return maskUshortGray(depthPng, confPng, depthImg, maxRangeMm);
            }
            // rtabmap iOS app은 ARDepthData를 RGBA 8-bit packed로 저장:
            //   R = depth_mm 상위 byte, G = 하위 byte, B=0, A=confidence flag.
            // ImageIO가 PNG RGBA를 TYPE_4BYTE_ABGR(또는 TYPE_INT_ARGB)로 로드.
            if (depthImg.getRaster().getNumBands() == 4) {
                return maskRgbaPacked(depthPng, confPng, depthImg, maxRangeMm);
            }
            // 그 외 비표준 — 그대로 통과
            return new Result(depthPng, confPng);
        } catch (IOException e) {
            log.warn("depth mask 실패, 원본 그대로 사용: {}", e.getMessage());
            return new Result(depthPng, confPng);
        }
    }

    private static Result maskUshortGray(
            byte[] depthPng, byte[] confPng, BufferedImage depthImg, int maxRangeMm
    ) throws IOException {
        short[] depthArr = ((DataBufferUShort) depthImg.getRaster().getDataBuffer()).getData();
        BufferedImage confImg = null;
        byte[] confArr = null;
        if (confPng != null) {
            confImg = ImageIO.read(new ByteArrayInputStream(confPng));
            if (confImg != null && confImg.getType() == BufferedImage.TYPE_BYTE_GRAY
                    && depthArr.length == confImg.getWidth() * confImg.getHeight()) {
                confArr = ((DataBufferByte) confImg.getRaster().getDataBuffer()).getData();
            }
        }
        boolean mutated = false;
        for (int i = 0; i < depthArr.length; i++) {
            int d = depthArr[i] & 0xFFFF;
            if (d > maxRangeMm) {
                depthArr[i] = 0;
                if (confArr != null) confArr[i] = 0;
                mutated = true;
            }
        }
        if (!mutated) return new Result(depthPng, confPng);
        ByteArrayOutputStream depthOut = new ByteArrayOutputStream(depthPng.length);
        ImageIO.write(depthImg, "png", depthOut);
        byte[] confResult = confPng;
        if (confArr != null && confImg != null) {
            ByteArrayOutputStream confOut = new ByteArrayOutputStream(confPng.length);
            ImageIO.write(confImg, "png", confOut);
            confResult = confOut.toByteArray();
        }
        return new Result(depthOut.toByteArray(), confResult);
    }

    private static Result maskRgbaPacked(
            byte[] depthPng, byte[] confPng, BufferedImage depthImg, int maxRangeMm
    ) throws IOException {
        int w = depthImg.getWidth();
        int h = depthImg.getHeight();
        // sample 단위 접근 — TYPE_INT_ARGB / TYPE_4BYTE_ABGR 어느 쪽이든 동작.
        int[] samples = new int[w * h * 4];
        depthImg.getRaster().getPixels(0, 0, w, h, samples);
        boolean mutated = false;
        for (int i = 0; i < samples.length; i += 4) {
            int r = samples[i];     // R = high byte
            int g = samples[i + 1]; // G = low byte
            int depthMm = (r << 8) | g;
            if (depthMm > maxRangeMm) {
                samples[i] = 0;       // R
                samples[i + 1] = 0;   // G
                samples[i + 2] = 0;   // B (이미 0이지만 안전하게)
                samples[i + 3] = 0;   // A (confidence) 도 reset
                mutated = true;
            }
        }
        if (!mutated) return new Result(depthPng, confPng);
        depthImg.getRaster().setPixels(0, 0, w, h, samples);
        ByteArrayOutputStream depthOut = new ByteArrayOutputStream(depthPng.length);
        ImageIO.write(depthImg, "png", depthOut);
        // RGBA depth는 alpha 채널에 confidence 들어가 있어 별도 confPng 없는 경우 多.
        return new Result(depthOut.toByteArray(), confPng);
    }
}
