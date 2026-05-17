package kr.ac.koreatech.indoor.vps.contexts.mapping.application.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.BuildFailureReason;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.BuildState;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildJobEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildJobRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapEdgeRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapNodeRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.ScanIngestRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.rtabmap.RtabmapGraphReader;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.rtabmap.RtabmapGraphReader.RtabmapGraph;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.rtabmap.RtabmapReprocessService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.rtabmap.RtabmapReprocessService.RtabmapReprocessException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.rtabmap.RtabmapReprocessService.RtabmapReprocessResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class BuildJobRunner {
    private final BuildJobRepository buildJobRepository;
    private final ScanIngestRepository scanIngestRepository;
    private final MapNodeRepository mapNodeRepository;
    private final MapEdgeRepository mapEdgeRepository;
    private final RtabmapGraphReader graphReader;
    private final RtabmapReprocessService reprocessService;
    private final IndoorProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final String workerId = "spring-build-worker-" + UUID.randomUUID();

    public BuildJobRunner(
            BuildJobRepository buildJobRepository,
            ScanIngestRepository scanIngestRepository,
            MapNodeRepository mapNodeRepository,
            MapEdgeRepository mapEdgeRepository,
            RtabmapGraphReader graphReader,
            RtabmapReprocessService reprocessService,
            IndoorProperties properties,
            TransactionTemplate transactionTemplate
    ) {
        this.buildJobRepository = buildJobRepository;
        this.scanIngestRepository = scanIngestRepository;
        this.mapNodeRepository = mapNodeRepository;
        this.mapEdgeRepository = mapEdgeRepository;
        this.graphReader = graphReader;
        this.reprocessService = reprocessService;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
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
            JobInput input = transactionTemplate.execute(status -> jobInput(buildJobId));
            if (input == null) {
                return;
            }
            if (!Files.exists(input.dbPath())) {
                throw new BuildInputException("rtabmap.db not found at " + input.dbPath());
            }
            RtabmapReprocessResult reprocess = reprocessService.reprocess(input.scanId(), input.dbPath());
            Path graphDbPath = reprocess.effectiveDbPath();
            RtabmapGraph graph = graphReader.read(graphDbPath, input.scanId(), buildJobId);
            if (graph.nodes().isEmpty()) {
                throw new BuildInputException("rtabmap graph has no nodes");
            }
            transactionTemplate.executeWithoutResult(status -> persistSuccess(buildJobId, input, graph, reprocess));
        } catch (BuildInputException | RtabmapGraphReader.RtabmapGraphReadException e) {
            markFailure(buildJobId, BuildFailureReason.rtabmap_data_not_ready, e.getMessage());
        } catch (RtabmapReprocessException e) {
            markFailure(buildJobId, BuildFailureReason.rtabmap_data_not_ready, e.getMessage());
        } catch (RuntimeException e) {
            markFailure(buildJobId, BuildFailureReason.internal, e.getMessage());
        }
    }

    private JobInput jobInput(UUID buildJobId) {
        BuildJobEntity job = buildJobRepository.findById(buildJobId).orElseThrow();
        if (job.getState() != BuildState.running) {
            return null;
        }
        return new JobInput(
                job.getScan().getScanId(),
                rtabmapDbPath(job.getScan().getStoragePath())
        );
    }

    private void persistSuccess(
            UUID buildJobId,
            JobInput input,
            RtabmapGraph graph,
            RtabmapReprocessResult reprocess
    ) {
        BuildJobEntity job = buildJobRepository.findById(buildJobId).orElseThrow();
        if (job.getState() != BuildState.running) {
            return;
        }
        job.markPersisting();
        mapEdgeRepository.deleteByScanId(input.scanId());
        mapNodeRepository.deleteByScanId(input.scanId());
        mapNodeRepository.saveAll(graph.nodes());
        mapEdgeRepository.saveAll(graph.edges());

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("build_source", reprocess.hasUsableOutput()
                ? "rtabmap_reprocessed_sqlite"
                : "rtabmap_node_link_sqlite");
        counts.put("map_nodes", graph.nodes().size());
        counts.put("map_edges", graph.edges().size());
        counts.put("rtabmap", Map.of(
                "db_path", reprocess.effectiveDbPath().toString(),
                "raw_db_path", input.dbPath().toString(),
                "reprocess", reprocess.metadata()
        ));
        job.markSucceeded(counts);
        job.getScan().changeBuildState(BuildState.succeeded);
        buildJobRepository.saveAndFlush(job);
        scanIngestRepository.saveAndFlush(job.getScan());
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

    private static class BuildInputException extends RuntimeException {
        BuildInputException(String message) {
            super(message);
        }
    }

    private record JobInput(UUID scanId, Path dbPath) {
    }
}
