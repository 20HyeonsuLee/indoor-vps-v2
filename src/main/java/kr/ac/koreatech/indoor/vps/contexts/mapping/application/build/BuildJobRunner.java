package kr.ac.koreatech.indoor.vps.contexts.mapping.application.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.BuildSuperpointIndexRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.BuildSuperpointIndexResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.ExportPointcloudRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.ExportPointcloudResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.MergeScanBridgeRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.PythonBridge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.BuildFailureReason;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.BuildState;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildJobEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildJobRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.ScanIngestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class BuildJobRunner {
    private static final Logger log = LoggerFactory.getLogger(BuildJobRunner.class);

    private final BuildJobRepository buildJobRepository;
    private final ScanIngestRepository scanIngestRepository;
    private final IndoorProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final PythonBridge pythonBridge;
    private final String workerId = "spring-build-worker-" + UUID.randomUUID();

    public BuildJobRunner(
            BuildJobRepository buildJobRepository,
            ScanIngestRepository scanIngestRepository,
            IndoorProperties properties,
            TransactionTemplate transactionTemplate,
            PythonBridge pythonBridge
    ) {
        this.buildJobRepository = buildJobRepository;
        this.scanIngestRepository = scanIngestRepository;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.pythonBridge = pythonBridge;
    }

    @Scheduled(fixedDelayString = "${indoor.build-worker.poll-interval-ms:2000}")
    public void runPendingJobs() {
        if (!properties.getBuildWorker().isEnabled()) {
            return;
        }
        int batchSize = Math.max(1, properties.getBuildWorker().getBatchSize());
        for (int i = 0; i < batchSize; i++) {
            UUID buildJobId = transactionTemplate.execute(status -> claimNextJob().orElse(null));
            if (buildJobId == null) {
                return;
            }
            processClaimedJob(buildJobId);
        }
    }

    private Optional<UUID> claimNextJob() {
        Optional<BuildJobEntity> pending = buildJobRepository.lockNextPendingJob();
        pending.ifPresent(this::markClaimed);
        return pending.map(BuildJobEntity::getBuildJobId);
    }

    private void markClaimed(BuildJobEntity job) {
        job.markRunning(workerId, Instant.now());
        job.getScan().changeBuildState(BuildState.running);
        buildJobRepository.saveAndFlush(job);
        scanIngestRepository.saveAndFlush(job.getScan());
    }

    private void processClaimedJob(UUID buildJobId) {
        try {
            Optional<JobInput> inputOpt = transactionTemplate.execute(status -> jobInput(buildJobId));
            if (inputOpt == null || inputOpt.isEmpty()) {
                return;
            }
            JobInput input = inputOpt.get();
            runMergeIfNeeded(input);
            if (!Files.exists(input.dbPath())) {
                throw new BuildInputException("rtabmap.db not found at " + input.dbPath());
            }
            Map<String, Object> counts = buildSuperpointIndex(input.scanId(), input.dbPath());
            exportPointcloud(input.scanId(), input.dbPath(), counts);
            markSuccess(buildJobId, counts);
        } catch (BuildInputException e) {
            markFailure(buildJobId, BuildFailureReason.rtabmap_data_not_ready, e.getMessage());
        } catch (RuntimeException e) {
            markFailure(buildJobId, BuildFailureReason.internal, e.getMessage());
        }
    }

    /**
     * scan.device_info에 merge.sources가 존재하고 dbPath가 아직 없으면 머지를 먼저 실행한다.
     * 머지 산출물(rtabmap.db)이 이미 있으면 건너뛴다 (재시도 안전).
     * merge.sources가 없는 일반 스캔은 이 메서드가 no-op이다.
     */
    @SuppressWarnings("unchecked")
    private void runMergeIfNeeded(JobInput input) {
        if (Files.exists(input.dbPath())) {
            return;
        }
        List<String> sources = transactionTemplate.execute(status -> {
            return scanIngestRepository.findById(input.scanId())
                    .map(scan -> {
                        Map<String, Object> deviceInfo = scan.getDeviceInfo();
                        if (deviceInfo == null) {
                            return null;
                        }
                        Object mergeSection = deviceInfo.get("merge");
                        if (!(mergeSection instanceof Map<?, ?> mergeMap)) {
                            return null;
                        }
                        Object rawSources = mergeMap.get("sources");
                        if (!(rawSources instanceof List<?> list)) {
                            return null;
                        }
                        return (List<String>) list;
                    })
                    .orElse(null);
        });
        if (sources == null || sources.isEmpty()) {
            return;
        }
        Path outputDir = input.dbPath().getParent();
        try {
            Files.createDirectories(outputDir);
        } catch (java.io.IOException e) {
            throw new BuildInputException("cannot create merge output dir: " + outputDir + " — " + e.getMessage());
        }
        log.info("[MergeBuild] merging {} sources into {} for scan {}", sources.size(), outputDir, input.scanId());
        pythonBridge.mergeScan(new MergeScanBridgeRequest(
                null,
                input.scanId(),
                sources,
                outputDir.toAbsolutePath().toString()
        ));
        log.info("[MergeBuild] merge complete for scan {}", input.scanId());
    }

    /**
     * python.enabled == false: SuperPoint skip, succeeded 처리 (no-op, 테스트 보존).
     * python.enabled == true 이고 throw: 호출자에게 예외 전파 → build_job FAIL.
     */
    private Map<String, Object> buildSuperpointIndex(UUID scanId, Path dbPath) {
        if (!properties.getPython().isEnabled()) {
            log.debug("[SuperPoint] python bridge disabled — skipping index build for scan {}", scanId);
            Map<String, Object> counts = new LinkedHashMap<>();
            counts.put("superpoint", "skipped");
            counts.put("reason", "python_disabled");
            return counts;
        }
        BuildSuperpointIndexResponse response = pythonBridge.buildSuperpointIndex(
                new BuildSuperpointIndexRequest(scanId.toString(), dbPath.toAbsolutePath().toString())
        );
        log.info(
                "[SuperPoint] index built for scan {}: frames={}, kp={}, elapsed={}ms, cacheDir={}",
                scanId, response.frameCount(), response.totalKeypoints(),
                response.elapsedMs(), response.cacheDir()
        );
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("superpoint", "built");
        counts.put("frameCount", response.frameCount());
        counts.put("totalKeypoints", response.totalKeypoints());
        counts.put("elapsedMs", response.elapsedMs());
        counts.put("cacheDir", response.cacheDir());
        counts.put("bytes", response.bytes());
        return counts;
    }

    /**
     * SuperPoint index 빌드 후 rtabmap.db에서 cloud.ply를 export (그래프 에디터 시각화용).
     * python.enabled == false: skip. throw 시 호출자에게 전파 → build_job FAIL.
     */
    private void exportPointcloud(UUID scanId, Path dbPath, Map<String, Object> counts) {
        if (!properties.getPython().isEnabled()) {
            log.debug("[Pointcloud] python bridge disabled — skipping export for scan {}", scanId);
            counts.put("pointcloud", "skipped");
            return;
        }
        Path outputPath = dbPath.toAbsolutePath().getParent().resolve("cloud.ply");
        ExportPointcloudResponse response = pythonBridge.exportPointcloud(
                new ExportPointcloudRequest(
                        scanId.toString(),
                        dbPath.toAbsolutePath().toString(),
                        outputPath.toString()
                )
        );
        log.info(
                "[Pointcloud] exported for scan {}: points={}, bytes={}, elapsed={}ms, path={}",
                scanId, response.pointCount(), response.fileSize(),
                response.elapsedMs(), response.plyPath()
        );
        counts.put("pointcloud", "exported");
        counts.put("plyPointCount", response.pointCount());
        counts.put("plyBytes", response.fileSize());
    }

    private Optional<JobInput> jobInput(UUID buildJobId) {
        BuildJobEntity job = buildJobRepository.findById(buildJobId).orElseThrow();
        if (job.getState() != BuildState.running) {
            return Optional.empty();
        }
        return Optional.of(new JobInput(
                job.getScan().getScanId(),
                rtabmapDbPath(job.getScan().getStoragePath())
        ));
    }

    private void markSuccess(UUID buildJobId, Map<String, Object> counts) {
        transactionTemplate.executeWithoutResult(status -> {
            BuildJobEntity job = buildJobRepository.findById(buildJobId).orElseThrow();
            if (job.getState() != BuildState.running) {
                return;
            }
            job.markSucceeded(counts);
            job.getScan().changeBuildState(BuildState.succeeded);
            buildJobRepository.saveAndFlush(job);
            scanIngestRepository.saveAndFlush(job.getScan());
        });
    }

    private void markFailure(UUID buildJobId, BuildFailureReason reason, String detail) {
        // python bridge timeout 등으로 설정된 thread interrupt flag를 clear.
        // 안 그러면 실패 처리 트랜잭션의 DB connection 획득이 interrupt로 깨져 build_job이 running에 고착된다.
        Thread.interrupted();
        transactionTemplate.executeWithoutResult(status -> {
            BuildJobEntity job = buildJobRepository.findById(buildJobId).orElseThrow();
            if (job.getState() != BuildState.running) {
                return;
            }
            job.markFailed(reason, detail);
            job.getScan().changeBuildState(BuildState.failed);
            buildJobRepository.saveAndFlush(job);
            scanIngestRepository.saveAndFlush(job.getScan());
        });
    }

    public void runJob(UUID buildJobId) {
        UUID claimed = transactionTemplate.execute(status -> buildJobRepository.lockPendingJobById(buildJobId)
                .map(job -> {
                    markClaimed(job);
                    return job.getBuildJobId();
                })
                .orElse(null));
        if (claimed != null) {
            processClaimedJob(claimed);
        }
    }

    private Path rtabmapDbPath(String storagePath) {
        Path path = Path.of(storagePath);
        if (!path.isAbsolute()) {
            path = properties.getStorageRoot().resolve(path);
        }
        Path fileName = path.getFileName();
        if (fileName != null && "rtabmap.db".equals(fileName.toString())) {
            return path;
        }
        if (fileName != null && fileName.toString().endsWith(".zip") && path.getParent() != null) {
            return path.getParent().resolve("rtabmap.db");
        }
        return path.resolve("rtabmap.db");
    }

    private static class BuildInputException extends RuntimeException {
        BuildInputException(String message) {
            super(message);
        }
    }

    private record JobInput(UUID scanId, Path dbPath) {
    }
}
