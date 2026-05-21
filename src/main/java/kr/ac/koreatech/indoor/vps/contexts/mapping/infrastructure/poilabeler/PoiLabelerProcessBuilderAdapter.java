package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.poilabeler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.port.PoiLabeler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * PoiLabeler 의 ProcessBuilder 구현 — python/tools/poi_labeler/label_pois.py 를
 * 별도 프로세스로 띄워 해당 scan 의 미처리 POI 를 분류한다.
 *
 * <p>fire-and-forget: 호출 즉시 반환 (@Async). 라벨러 실패는 빌드에 영향 없음.
 */
@Component
public class PoiLabelerProcessBuilderAdapter implements PoiLabeler {
    private static final Logger log = LoggerFactory.getLogger(PoiLabelerProcessBuilderAdapter.class);

    private final IndoorProperties.PoiLabeler properties;

    public PoiLabelerProcessBuilderAdapter(IndoorProperties properties) {
        this.properties = properties.getPoiLabeler();
    }

    @Override
    @Async("poiLabelerExecutor")
    public void labelScan(UUID scanId) {
        if (!properties.isEnabled()) {
            log.debug("[poi-labeler] disabled — skip scan {}", scanId);
            return;
        }
        Path workingDir = properties.getWorkingDir();
        Path entry = workingDir.resolve(properties.getEntryScript());
        if (!Files.exists(entry)) {
            log.warn("[poi-labeler] entry script not found: {}", entry);
            return;
        }
        runLabeler(scanId, workingDir);
    }

    private void runLabeler(UUID scanId, Path workingDir) {
        ProcessBuilder builder = new ProcessBuilder(
                properties.getExecutable(),
                properties.getEntryScript(),
                "--scan-id", scanId.toString()
        );
        builder.directory(workingDir.toFile());
        // 라벨러는 자체 logs/labeler-*.log 파일에 기록.
        // 콘솔 출력은 폐기하여 JVM 로그를 어지럽히지 않는다.
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        long startedAt = System.currentTimeMillis();
        log.info("[poi-labeler] start scan_id={}", scanId);
        try {
            Process process = builder.start();
            int exitCode = awaitCompletion(process, scanId);
            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.info("[poi-labeler] finished scan_id={} exit_code={} elapsed_ms={}",
                    scanId, exitCode, elapsedMs);
        } catch (IOException e) {
            log.warn("[poi-labeler] start failed scan_id={}: {}", scanId, e.getMessage());
        }
    }

    private int awaitCompletion(Process process, UUID scanId) {
        try {
            boolean finished = process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (finished) {
                return process.exitValue();
            }
            log.warn("[poi-labeler] timeout scan_id={} after {}s — destroying",
                    scanId, properties.getTimeoutSeconds());
            process.destroyForcibly();
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return -2;
        }
    }
}
