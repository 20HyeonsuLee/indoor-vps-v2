package kr.ac.koreatech.indoor.vps.contexts.mapping.application.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.PointcloudFileResolver;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.BuildSuperpointIndexRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.ExportPointcloudRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.PythonBridge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.BuildFailureReason;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.BuildState;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.port.PoiLabeler;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.port.RtabmapGraphReader;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.port.RtabmapGraphReader.RtabmapGraphReadException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildJobEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildJobRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.ScanIngestRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.RtabmapReprocessor;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.RtabmapReprocessor.RtabmapReprocessException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.RtabmapReprocessor.RtabmapReprocessResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.ScanMetadata;
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
    private final RtabmapGraphReader graphReader;
    private final RtabmapReprocessor reprocessService;
    private final ScanMetadataReader metadataReader;
    private final IndoorProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final BuildGraphPersister graphPersister;
    private final PythonBridge pythonBridge;
    private final PoiLabeler poiLabeler;
    private final String workerId = "spring-build-worker-" + UUID.randomUUID();

    public BuildJobRunner(
            BuildJobRepository buildJobRepository,
            ScanIngestRepository scanIngestRepository,
            RtabmapGraphReader graphReader,
            RtabmapReprocessor reprocessService,
            ScanMetadataReader metadataReader,
            IndoorProperties properties,
            TransactionTemplate transactionTemplate,
            BuildGraphPersister graphPersister,
            PythonBridge pythonBridge,
            PoiLabeler poiLabeler
    ) {
        this.buildJobRepository = buildJobRepository;
        this.scanIngestRepository = scanIngestRepository;
        this.graphReader = graphReader;
        this.reprocessService = reprocessService;
        this.metadataReader = metadataReader;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.graphPersister = graphPersister;
        this.pythonBridge = pythonBridge;
        this.poiLabeler = poiLabeler;
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
            if (!Files.exists(input.dbPath())) {
                throw new BuildInputException("rtabmap.db not found at " + input.dbPath());
            }
            RtabmapReprocessResult reprocess = reprocessService.reprocess(input.scanId(), input.dbPath());
            Path graphDbPath = reprocess.effectiveDbPath();
            buildSuperpointIndexQuietly(input.scanId(), graphDbPath);
            validateRtabmapGraph(graphDbPath, input.scanId(), buildJobId);
            Optional<ScanMetadata> metadata = metadataReader.read(metadataDbPath(input.dbPath()));
            transactionTemplate.executeWithoutResult(
                    status -> graphPersister.persistSuccess(buildJobId, input.scanId(), input.dbPath(), reprocess, metadata)
            );
            exportPointcloudQuietly(input.scanId(), graphDbPath);
            // 빌드 성공 직후 fire-and-forget 으로 POI 라벨러 트리거.
            // poiLabeler.isEnabled() = false 면 어댑터 안에서 즉시 return.
            poiLabeler.labelScan(input.scanId());
        } catch (BuildInputException | RtabmapGraphReadException e) {
            markFailure(buildJobId, BuildFailureReason.rtabmap_data_not_ready, e.getMessage());
        } catch (RtabmapReprocessException e) {
            markFailure(buildJobId, BuildFailureReason.rtabmap_data_not_ready, e.getMessage());
        } catch (RuntimeException e) {
            markFailure(buildJobId, BuildFailureReason.internal, e.getMessage());
        }
    }

    private void buildSuperpointIndexQuietly(UUID scanId, Path dbPath) {
        if (!properties.getPython().isEnabled()) {
            log.debug("[SuperPoint] python bridge disabled — skipping index build for scan {}", scanId);
            return;
        }
        try {
            var response = pythonBridge.buildSuperpointIndex(
                    new BuildSuperpointIndexRequest(scanId.toString(), dbPath.toAbsolutePath().toString())
            );
            log.info(
                    "[SuperPoint] index built for scan {}: frames={}, kp={}, elapsed={}ms, cacheDir={}",
                    scanId, response.frameCount(), response.totalKeypoints(),
                    response.elapsedMs(), response.cacheDir()
            );
        } catch (Exception e) {
            log.warn("[SuperPoint] index build failed for scan {} — localize will rebuild on demand: {}",
                    scanId, e.getMessage());
        }
    }

    private void exportPointcloudQuietly(UUID scanId, Path dbPath) {
        if (!properties.getPython().isEnabled()) {
            log.debug("[Pointcloud] python bridge disabled — skipping export for scan {}", scanId);
            return;
        }
        Path output = dbPath.resolveSibling(PointcloudFileResolver.CLOUD_FILE_NAME);
        try {
            var response = pythonBridge.exportPointcloud(new ExportPointcloudRequest(
                    scanId.toString(),
                    dbPath.toAbsolutePath().toString(),
                    output.toAbsolutePath().toString()
            ));
            log.info("[Pointcloud] exported scan {}: points={}, size={}, elapsed={}ms",
                    scanId, response.pointCount(), response.fileSize(), response.elapsedMs());
        } catch (Exception e) {
            log.warn("[Pointcloud] export failed for scan {} — viewer will 404 until retry: {}",
                    scanId, e.getMessage());
        }
    }

    private void validateRtabmapGraph(Path graphDbPath, UUID scanId, UUID buildJobId) {
        var graph = graphReader.read(graphDbPath, scanId, buildJobId);
        if (graph.nodes().isEmpty()) {
            throw new BuildInputException("rtabmap graph has no nodes");
        }
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

    private void markFailure(UUID buildJobId, BuildFailureReason reason, String detail) {
        transactionTemplate.executeWithoutResult(status -> {
            BuildJobEntity job = buildJobRepository.findById(buildJobId).orElseThrow();
            if (job.getState() == BuildState.succeeded) {
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

    private Path metadataDbPath(Path rtabmapDbPath) {
        return rtabmapDbPath.resolveSibling("scan_metadata.db");
    }

    private static class BuildInputException extends RuntimeException {
        BuildInputException(String message) {
            super(message);
        }
    }

    private record JobInput(UUID scanId, Path dbPath) {
    }
}
