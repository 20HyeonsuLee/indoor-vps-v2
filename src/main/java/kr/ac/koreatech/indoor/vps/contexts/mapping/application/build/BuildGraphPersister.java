package kr.ac.koreatech.indoor.vps.contexts.mapping.application.build;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.BuildState;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.port.RtabmapGraphReader.RtabmapGraph;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildJobEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildJobRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapEdgeRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapNodeRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.ScanIngestRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.RtabmapReprocessor.RtabmapReprocessResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
class BuildGraphPersister {
    private final BuildJobRepository buildJobRepository;
    private final ScanIngestRepository scanIngestRepository;
    private final MapNodeRepository mapNodeRepository;
    private final MapEdgeRepository mapEdgeRepository;

    BuildGraphPersister(
            BuildJobRepository buildJobRepository,
            ScanIngestRepository scanIngestRepository,
            MapNodeRepository mapNodeRepository,
            MapEdgeRepository mapEdgeRepository
    ) {
        this.buildJobRepository = buildJobRepository;
        this.scanIngestRepository = scanIngestRepository;
        this.mapNodeRepository = mapNodeRepository;
        this.mapEdgeRepository = mapEdgeRepository;
    }

    void persistSuccess(UUID buildJobId, UUID scanId, java.nio.file.Path rawDbPath,
                        RtabmapGraph graph, RtabmapReprocessResult reprocess) {
        BuildJobEntity job = buildJobRepository.findById(buildJobId).orElseThrow();
        if (job.getState() != BuildState.running) {
            return;
        }
        job.markPersisting();
        mapEdgeRepository.deleteByScanId(scanId);
        mapNodeRepository.deleteByScanId(scanId);
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
                "raw_db_path", rawDbPath.toString(),
                "reprocess", reprocess.metadata()
        ));
        job.markSucceeded(counts);
        job.getScan().changeBuildState(BuildState.succeeded);
        buildJobRepository.saveAndFlush(job);
        scanIngestRepository.saveAndFlush(job.getScan());
    }
}
