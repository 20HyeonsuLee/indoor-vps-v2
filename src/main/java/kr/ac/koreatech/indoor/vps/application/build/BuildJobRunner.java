package kr.ac.koreatech.indoor.vps.application.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.application.build.RtabmapGraphReader.RtabmapGraph;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.BuildJobEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.DbEnums.BuildFailureReason;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.DbEnums.BuildState;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.ScanIngestEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.BuildJobRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.MapEdgeRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.MapNodeRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.ScanIngestRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class BuildJobRunner {
    private final BuildJobRepository buildJobRepository;
    private final ScanIngestRepository scanIngestRepository;
    private final MapNodeRepository mapNodeRepository;
    private final MapEdgeRepository mapEdgeRepository;
    private final RtabmapGraphReader graphReader;
    private final IndoorProperties properties;

    public BuildJobRunner(
            BuildJobRepository buildJobRepository,
            ScanIngestRepository scanIngestRepository,
            MapNodeRepository mapNodeRepository,
            MapEdgeRepository mapEdgeRepository,
            RtabmapGraphReader graphReader,
            IndoorProperties properties
    ) {
        this.buildJobRepository = buildJobRepository;
        this.scanIngestRepository = scanIngestRepository;
        this.mapNodeRepository = mapNodeRepository;
        this.mapEdgeRepository = mapEdgeRepository;
        this.graphReader = graphReader;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${indoor.build-worker.poll-interval-ms:2000}")
    @Transactional
    public void runPendingJobs() {
        if (!properties.getBuildWorker().isEnabled()) {
            return;
        }
        List<BuildJobEntity> jobs = buildJobRepository.findByStateOrderByEnqueuedAtAsc(
                BuildState.pending,
                Limit.of(Math.max(1, properties.getBuildWorker().getBatchSize()))
        );
        for (BuildJobEntity job : jobs) {
            runJob(job.getBuildJobId());
        }
    }

    @Transactional
    public void runJob(UUID buildJobId) {
        BuildJobEntity job = buildJobRepository.findById(buildJobId)
                .orElseThrow();
        if (job.getState() != BuildState.pending) {
            return;
        }
        ScanIngestEntity scan = job.getScan();
        UUID scanId = scan.getScanId();
        Path dbPath = rtabmapDbPath(scan.getStoragePath());
        try {
            job.markRunning();
            scan.setBuildState(BuildState.running);
            buildJobRepository.saveAndFlush(job);
            scanIngestRepository.saveAndFlush(scan);

            if (!Files.exists(dbPath)) {
                throw new BuildInputException("rtabmap.db not found at " + dbPath);
            }
            RtabmapGraph graph = graphReader.read(dbPath, scanId, job.getBuildJobId());
            if (graph.nodes().isEmpty()) {
                throw new BuildInputException("rtabmap graph has no nodes");
            }

            job.markPersisting();
            mapEdgeRepository.deleteByScanId(scanId);
            mapNodeRepository.deleteByScanId(scanId);
            mapNodeRepository.saveAll(graph.nodes());
            mapEdgeRepository.saveAll(graph.edges());

            Map<String, Object> counts = new LinkedHashMap<>();
            counts.put("build_source", "rtabmap_node_link_sqlite");
            counts.put("map_nodes", graph.nodes().size());
            counts.put("map_edges", graph.edges().size());
            counts.put("rtabmap", Map.of("db_path", dbPath.toString()));
            job.markSucceeded(counts);
            scan.setBuildState(BuildState.succeeded);
        } catch (BuildInputException | RtabmapGraphReader.RtabmapGraphReadException e) {
            job.markFailed(BuildFailureReason.rtabmap_data_not_ready, e.getMessage());
            scan.setBuildState(BuildState.failed);
        } catch (RuntimeException e) {
            job.markFailed(BuildFailureReason.internal, e.getMessage());
            scan.setBuildState(BuildState.failed);
        }
        buildJobRepository.saveAndFlush(job);
        scanIngestRepository.saveAndFlush(scan);
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
}
