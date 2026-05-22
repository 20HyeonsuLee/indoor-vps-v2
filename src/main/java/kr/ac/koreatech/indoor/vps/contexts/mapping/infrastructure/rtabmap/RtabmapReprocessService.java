package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.rtabmap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.RtabmapReprocessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RtabmapReprocessService implements RtabmapReprocessor {
    private static final Logger log = LoggerFactory.getLogger(RtabmapReprocessService.class);

    private final IndoorProperties properties;

    public RtabmapReprocessService(IndoorProperties properties) {
        this.properties = properties;
    }

    @Override
    public RtabmapReprocessResult reprocess(UUID scanId, Path inputDb) {
        IndoorProperties.Rtabmap.Reprocess config = properties.getRtabmap().getReprocess();
        if (!config.isEnabled()) {
            return RtabmapReprocessResult.skipped("disabled", inputDb, null, null);
        }
        if (!Files.exists(inputDb)) {
            return RtabmapReprocessResult.skipped("input_db_missing", inputDb, null, null);
        }
        try {
            ensureRtabmapCompatibility(inputDb);
        } catch (IOException | SQLException e) {
            RtabmapReprocessResult result = RtabmapReprocessResult.failed(
                    "schema_prepare_failed",
                    inputDb,
                    null,
                    null,
                    List.of(),
                    Duration.ZERO,
                    null,
                    "",
                    e.getMessage()
            );
            return failOrFallback(config, result);
        }
        Optional<Path> binary = RtabmapProcessUtils.resolveExecutable(config.getExecutable());
        if (binary.isEmpty()) {
            RtabmapReprocessResult result = RtabmapReprocessResult.skipped(
                    "binary_not_available",
                    inputDb,
                    null,
                    Map.of("executable", config.getExecutable())
            );
            if (config.isRequired()) {
                throw new RtabmapReprocessException("rtabmap-reprocess binary not available: " + config.getExecutable(), result);
            }
            return result;
        }

        Path outputDb = inputDb.getParent().resolve("rtabmap_reprocessed.db");
        if (Files.exists(outputDb) && RtabmapProcessUtils.hasGraphRows(outputDb)) {
            return RtabmapReprocessResult.alreadyReprocessed(inputDb, outputDb, binary.get());
        }
        if (Files.exists(outputDb)) {
            try {
                Files.delete(outputDb);
            } catch (IOException e) {
                RtabmapReprocessResult result = RtabmapReprocessResult.failed(
                        "stale_output_delete_failed",
                        inputDb,
                        outputDb,
                        binary.get(),
                        List.of(),
                        Duration.ZERO,
                        null,
                        "",
                        e.getMessage()
                );
                return failOrFallback(config, result);
            }
        }

        Path stdoutLog = inputDb.getParent().resolve("rtabmap_reprocess.stdout.log");
        Path stderrLog = inputDb.getParent().resolve("rtabmap_reprocess.stderr.log");
        List<String> command = List.of(
                binary.get().toString(),
                "--Kp/DetectorStrategy",
                "1",
                "--Vis/FeatureType",
                "1",
                "--Mem/ImagePreDecimation",
                "1",
                // DepthAsMask=true(default)는 depth invalid 픽셀의 feature를 컷.
                // 우리는 5m masking + ARKit raw로 valid depth가 8%대라 SURF 풀이 너무
                // sparse → LC hypothesis는 잡혀도 transform RANSAC이 inlier 부족으로
                // 다 reject(Total LC=0). false로 풀어 RGB 전체에서 feature 추출.
                "--Mem/DepthAsMask",
                "false",
                inputDb.toString(),
                outputDb.toString()
        );

        try {
            Files.deleteIfExists(outputDb);
            Files.createDirectories(inputDb.getParent());
            Instant startedAt = Instant.now();
            Process process = new ProcessBuilder(command)
                    .redirectOutput(stdoutLog.toFile())
                    .redirectError(stderrLog.toFile())
                    .start();
            boolean finished = process.waitFor(Math.max(1, config.getTimeoutSeconds()), TimeUnit.SECONDS);
            Duration duration = Duration.between(startedAt, Instant.now());
            if (!finished) {
                process.destroyForcibly();
                RtabmapReprocessResult result = RtabmapReprocessResult.failed(
                        "timeout",
                        inputDb,
                        outputDb,
                        binary.get(),
                        command,
                        duration,
                        -1,
                        RtabmapProcessUtils.tail(stdoutLog),
                        RtabmapProcessUtils.tail(stderrLog)
                );
                return failOrFallback(config, result);
            }
            if (process.exitValue() != 0 || !Files.exists(outputDb)) {
                RtabmapReprocessResult result = RtabmapReprocessResult.failed(
                        "exit_" + process.exitValue(),
                        inputDb,
                        outputDb,
                        binary.get(),
                        command,
                        duration,
                        process.exitValue(),
                        RtabmapProcessUtils.tail(stdoutLog),
                        RtabmapProcessUtils.tail(stderrLog)
                );
                return failOrFallback(config, result);
            }
            detectMoreLoopClosuresQuietly(outputDb);
            return RtabmapReprocessResult.succeeded(
                    inputDb,
                    outputDb,
                    binary.get(),
                    command,
                    duration,
                    RtabmapProcessUtils.tail(stdoutLog),
                    RtabmapProcessUtils.tail(stderrLog)
            );
        } catch (IOException e) {
            RtabmapReprocessResult result = RtabmapReprocessResult.failed(
                    "io_error",
                    inputDb,
                    outputDb,
                    binary.get(),
                    command,
                    Duration.ZERO,
                    null,
                    "",
                    e.getMessage()
            );
            return failOrFallback(config, result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            RtabmapReprocessResult result = RtabmapReprocessResult.failed(
                    "interrupted",
                    inputDb,
                    outputDb,
                    binary.get(),
                    command,
                    Duration.ZERO,
                    null,
                    "",
                    e.getMessage()
            );
            return failOrFallback(config, result);
        }
    }

    private RtabmapReprocessResult failOrFallback(
            IndoorProperties.Rtabmap.Reprocess config,
            RtabmapReprocessResult result
    ) {
        if (config.isRequired()) {
            throw new RtabmapReprocessException("rtabmap-reprocess failed: " + result.reason(), result);
        }
        log.warn("rtabmap-reprocess failed; using raw rtabmap.db. reason={}", result.reason());
        return result;
    }

    private void ensureRtabmapCompatibility(Path dbPath) throws IOException, SQLException {
        RtabmapSchemaFixer.ensureCompatibility(dbPath);
    }

    /**
     * reprocess가 끝난 db에 rtabmap-detectMoreLoopClosures를 in-place로 적용해
     * cluster radius 1m / yaw 30° 안의 노드 페어를 visual 2D-2D 매칭으로 검출하고
     * graph 최적화 결과(Admin.opt_poses)를 갱신한다.
     *
     * <p>기본 BoW LC는 우리 ARKit 복도 데이터에서 perceptual aliasing 때문에
     * 모든 verify가 reject(LC=0)됨이 확인됨. odom 근접도 + EstimationType=2(depth
     * 무관 epipolar)로 우회하면 동일 데이터에서 LC 57개 검출됨.
     *
     * <p>실패는 warn 로그만 남기고 reprocess 결과 그대로 사용 (fail-soft).
     */
    private void detectMoreLoopClosuresQuietly(Path reprocessedDb) {
        Optional<Path> binary = RtabmapProcessUtils.resolveExecutable("rtabmap-detectMoreLoopClosures");
        if (binary.isEmpty()) {
            log.debug("[LC] rtabmap-detectMoreLoopClosures binary not on PATH — skipping");
            return;
        }
        Path stdoutLog = reprocessedDb.getParent().resolve("rtabmap_lc.stdout.log");
        Path stderrLog = reprocessedDb.getParent().resolve("rtabmap_lc.stderr.log");
        List<String> command = List.of(
                binary.get().toString(),
                "-r", "1",
                "-a", "30",
                "-i", "1",
                "--intra",
                "--Vis/EstimationType", "2",
                "--Vis/MinInliers", "10",
                reprocessedDb.toString()
        );
        try {
            Instant startedAt = Instant.now();
            Process process = new ProcessBuilder(command)
                    .redirectOutput(stdoutLog.toFile())
                    .redirectError(stderrLog.toFile())
                    .start();
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            Duration duration = Duration.between(startedAt, Instant.now());
            if (!finished) {
                process.destroyForcibly();
                log.warn("[LC] detectMoreLoopClosures timed out after 120s for {}", reprocessedDb);
                return;
            }
            if (process.exitValue() != 0) {
                log.warn("[LC] detectMoreLoopClosures exit={} for {} (stderr tail: {})",
                        process.exitValue(), reprocessedDb, RtabmapProcessUtils.tail(stderrLog));
                return;
            }
            log.info("[LC] detectMoreLoopClosures done in {}ms for {}", duration.toMillis(), reprocessedDb);
        } catch (IOException e) {
            log.warn("[LC] detectMoreLoopClosures io error: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[LC] detectMoreLoopClosures interrupted: {}", e.getMessage());
        }
    }

}
