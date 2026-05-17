package kr.ac.koreatech.indoor.vps.contexts.mapping.application.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.BuildFailureReason;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.BuildState;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.port.RtabmapGraphReader;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.port.RtabmapGraphReader.RtabmapGraph;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.port.RtabmapGraphReader.RtabmapGraphReadException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildJobEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildJobRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.ScanIngestRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.RtabmapReprocessor;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.RtabmapReprocessor.RtabmapReprocessException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.RtabmapReprocessor.RtabmapReprocessResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class BuildJobRunner {
    private final BuildJobRepository buildJobRepository;
    private final ScanIngestRepository scanIngestRepository;
    private final RtabmapGraphReader graphReader;
    private final RtabmapReprocessor reprocessService;
    private final IndoorProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final BuildGraphPersister graphPersister;
    private final String workerId = "spring-build-worker-" + UUID.randomUUID();

    public BuildJobRunner(
            BuildJobRepository buildJobRepository,
            ScanIngestRepository scanIngestRepository,
            RtabmapGraphReader graphReader,
            RtabmapReprocessor reprocessService,
            IndoorProperties properties,
            TransactionTemplate transactionTemplate,
            BuildGraphPersister graphPersister
    ) {
        this.buildJobRepository = buildJobRepository;
        this.scanIngestRepository = scanIngestRepository;
        this.graphReader = graphReader;
        this.reprocessService = reprocessService;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.graphPersister = graphPersister;
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
            RtabmapGraph graph = graphReader.read(graphDbPath, input.scanId(), buildJobId);
            if (graph.nodes().isEmpty()) {
                throw new BuildInputException("rtabmap graph has no nodes");
            }
            transactionTemplate.executeWithoutResult(
                    status -> graphPersister.persistSuccess(buildJobId, input.scanId(), input.dbPath(), graph, reprocess)
            );
        } catch (BuildInputException | RtabmapGraphReadException e) {
            markFailure(buildJobId, BuildFailureReason.rtabmap_data_not_ready, e.getMessage());
        } catch (RtabmapReprocessException e) {
            markFailure(buildJobId, BuildFailureReason.rtabmap_data_not_ready, e.getMessage());
        } catch (RuntimeException e) {
            markFailure(buildJobId, BuildFailureReason.internal, e.getMessage());
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

    private static class BuildInputException extends RuntimeException {
        BuildInputException(String message) {
            super(message);
        }
    }

    private record JobInput(UUID scanId, Path dbPath) {
    }
}
