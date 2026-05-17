package kr.ac.koreatech.indoor.vps.contexts.mapping.application.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.build.ScanMetadataIntegrator.IntegrationResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.BuildState;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.port.RtabmapGraphReader.RtabmapGraph;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildJobEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.ScanIngestEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildJobRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapEdgeRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapNodeRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.PoiCanonicalRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.ScanIngestRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.VerticalConnectorStopRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.RtabmapReprocessor.RtabmapReprocessResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.ScanMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
class BuildGraphPersister {
    private final BuildJobRepository buildJobRepository;
    private final ScanIngestRepository scanIngestRepository;
    private final MapNodeRepository mapNodeRepository;
    private final MapEdgeRepository mapEdgeRepository;
    private final PoiCanonicalRepository poiCanonicalRepository;
    private final VerticalConnectorStopRepository verticalConnectorStopRepository;
    private final ScanMetadataIntegrator metadataIntegrator;

    BuildGraphPersister(
            BuildJobRepository buildJobRepository,
            ScanIngestRepository scanIngestRepository,
            MapNodeRepository mapNodeRepository,
            MapEdgeRepository mapEdgeRepository,
            PoiCanonicalRepository poiCanonicalRepository,
            VerticalConnectorStopRepository verticalConnectorStopRepository,
            ScanMetadataIntegrator metadataIntegrator
    ) {
        this.buildJobRepository = buildJobRepository;
        this.scanIngestRepository = scanIngestRepository;
        this.mapNodeRepository = mapNodeRepository;
        this.mapEdgeRepository = mapEdgeRepository;
        this.poiCanonicalRepository = poiCanonicalRepository;
        this.verticalConnectorStopRepository = verticalConnectorStopRepository;
        this.metadataIntegrator = metadataIntegrator;
    }

    void persistSuccess(
            UUID buildJobId,
            UUID scanId,
            java.nio.file.Path rawDbPath,
            RtabmapGraph graph,
            RtabmapReprocessResult reprocess,
            Optional<ScanMetadata> scanMetadata
    ) {
        BuildJobEntity job = buildJobRepository.findById(buildJobId).orElseThrow();
        if (job.getState() != BuildState.running) {
            return;
        }
        job.markPersisting();

        List<MapNodeEntity> allNodes = new ArrayList<>(graph.nodes());
        List<MapEdgeEntity> allEdges = new ArrayList<>(graph.edges());

        List<ScanMetadataIntegrator.IntegrationResult> integrations = new ArrayList<>();
        scanMetadata.ifPresent(metadata -> {
            ScanMetadataIntegrator.IntegrationResult integration =
                    metadataIntegrator.integrate(scanId, buildJobId, metadata, allNodes);
            allNodes.addAll(integration.extraNodes());
            allEdges.addAll(integration.extraEdges());
            integrations.add(integration);
            enrichDeviceInfo(job.getScan(), metadata);
        });

        mapEdgeRepository.deleteByScanId(scanId);
        mapNodeRepository.deleteByScanId(scanId);
        // map_node를 먼저 flush해 FK 참조(poi_canonical.route_node_id)가 만족되도록 보장
        mapNodeRepository.saveAllAndFlush(allNodes);
        mapEdgeRepository.saveAll(allEdges);

        // map_node flush 완료 후 poi_canonical, vertical_connector_stop 저장
        for (ScanMetadataIntegrator.IntegrationResult integration : integrations) {
            poiCanonicalRepository.saveAllAndFlush(integration.pois());
            verticalConnectorStopRepository.saveAll(integration.stops());
        }

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("build_source", reprocess.hasUsableOutput()
                ? "rtabmap_reprocessed_sqlite"
                : "rtabmap_node_link_sqlite");
        counts.put("map_nodes", allNodes.size());
        counts.put("map_edges", allEdges.size());
        counts.put("metadata_integrated", scanMetadata.isPresent());
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

    private void enrichDeviceInfo(ScanIngestEntity scan, ScanMetadata metadata) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (metadata.session().deviceModel() != null) {
            extra.put("device_model", metadata.session().deviceModel());
        }
        if (metadata.session().startedAt() != null) {
            extra.put("scan_started_at", metadata.session().startedAt());
        }
        scan.mergeDeviceInfo(extra);
    }
}
