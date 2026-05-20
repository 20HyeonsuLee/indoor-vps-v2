package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam.SLAMLocalizeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A/B 비교 결과를 storage root 안 jsonl 파일에 append. primary와 shadow 결과를
 * 한 줄씩 기록해 추후 grep/jq로 정확도 비교. fire-and-forget executor에서 호출.
 *
 * <p>I/O 실패 시 stderr warn만 남기고 (primary 응답에는 영향 없음).
 */
@Component
public class ShadowLocalizeLogger {
    private static final Logger log = LoggerFactory.getLogger(ShadowLocalizeLogger.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path logFile;

    public ShadowLocalizeLogger(IndoorProperties properties) {
        this.logFile = properties.getStorageRoot().resolve("localize_shadow.jsonl");
    }

    public void append(LocalizeShadowEntry entry) {
        try {
            Files.createDirectories(logFile.getParent());
            byte[] line = (MAPPER.writeValueAsString(entry.toJsonMap()) + "\n").getBytes(StandardCharsets.UTF_8);
            synchronized (this) {
                Files.write(logFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            log.warn("[shadow-log] append failed: {}", e.getMessage());
        }
    }

    public record LocalizeShadowEntry(
            String requestId,
            String buildingId,
            String floorId,
            int queryImageCount,
            int queryDepthCount,
            ResultSnapshot primary,
            ResultSnapshot shadow,
            DiffSnapshot diff
    ) {
        Map<String, Object> toJsonMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ts", Instant.now().toString());
            m.put("requestId", requestId);
            m.put("buildingId", buildingId);
            m.put("floorId", floorId);
            m.put("queryImageCount", queryImageCount);
            m.put("queryDepthCount", queryDepthCount);
            m.put("primary", primary == null ? null : primary.toJsonMap());
            m.put("shadow", shadow == null ? null : shadow.toJsonMap());
            m.put("diff", diff == null ? null : diff.toJsonMap());
            return m;
        }
    }

    public record ResultSnapshot(
            SLAMLocalizeResult result,
            long elapsedMs,
            String error
    ) {
        Map<String, Object> toJsonMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            if (result != null) {
                m.put("pose", result.pose());
                m.put("confidence", result.confidence());
                m.put("numMatches", result.numMatches());
                m.put("matchedImageIndex", result.matchedImageIndex());
                m.put("methodUsed", result.methodUsed());
                m.put("floorId", result.floorId());
                m.put("areaId", result.areaId());
                m.put("floorLevel", result.floorLevel());
            }
            m.put("elapsedMs", elapsedMs);
            m.put("error", error);
            return m;
        }
    }

    public record DiffSnapshot(double translationM, double yawDeg) {
        Map<String, Object> toJsonMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("translationM", translationM);
            m.put("yawDeg", yawDeg);
            return m;
        }
    }
}
