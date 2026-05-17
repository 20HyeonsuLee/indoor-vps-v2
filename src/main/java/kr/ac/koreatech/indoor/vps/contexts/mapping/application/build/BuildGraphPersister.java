package kr.ac.koreatech.indoor.vps.contexts.mapping.application.build;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.build.ScanMetadataIntegrator.IntegrationResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.BuildState;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildJobEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.ScanIngestEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildJobRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorAreaPolygonRepository;
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
    private final FloorAreaPolygonRepository polygonRepository;
    private final PoiCanonicalRepository poiCanonicalRepository;
    private final VerticalConnectorStopRepository verticalConnectorStopRepository;
    private final ScanMetadataIntegrator metadataIntegrator;

    BuildGraphPersister(
            BuildJobRepository buildJobRepository,
            ScanIngestRepository scanIngestRepository,
            MapNodeRepository mapNodeRepository,
            MapEdgeRepository mapEdgeRepository,
            FloorAreaPolygonRepository polygonRepository,
            PoiCanonicalRepository poiCanonicalRepository,
            VerticalConnectorStopRepository verticalConnectorStopRepository,
            ScanMetadataIntegrator metadataIntegrator
    ) {
        this.buildJobRepository = buildJobRepository;
        this.scanIngestRepository = scanIngestRepository;
        this.mapNodeRepository = mapNodeRepository;
        this.mapEdgeRepository = mapEdgeRepository;
        this.polygonRepository = polygonRepository;
        this.poiCanonicalRepository = poiCanonicalRepository;
        this.verticalConnectorStopRepository = verticalConnectorStopRepository;
        this.metadataIntegrator = metadataIntegrator;
    }

    void persistSuccess(
            UUID buildJobId,
            UUID scanId,
            Path rawDbPath,
            RtabmapReprocessResult reprocess,
            Optional<ScanMetadata> scanMetadata
    ) {
        BuildJobEntity job = buildJobRepository.findById(buildJobId).orElseThrow();
        if (job.getState() != BuildState.running) {
            return;
        }
        job.markPersisting();

        mapEdgeRepository.deleteByScanId(scanId);
        mapNodeRepository.deleteByScanId(scanId);
        polygonRepository.deleteByScanId(scanId);

        int nodeCount = 0;
        int edgeCount = 0;
        int polygonCount = 0;

        if (scanMetadata.isPresent()) {
            ScanMetadata metadata = scanMetadata.get();
            IntegrationResult result = metadataIntegrator.integrate(scanId, buildJobId, job.getAreaId(), metadata);
            mapNodeRepository.saveAllAndFlush(result.nodes());
            mapEdgeRepository.saveAll(result.edges());
            polygonRepository.saveAll(result.polygons());
            poiCanonicalRepository.saveAllAndFlush(result.pois());
            verticalConnectorStopRepository.saveAllAndFlush(result.stops());
            enrichDeviceInfo(job.getScan(), metadata);
            nodeCount = result.nodes().size();
            edgeCount = result.edges().size();
            polygonCount = result.polygons().size();
        }

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("build_source", "scan_metadata_only");
        counts.put("map_nodes", nodeCount);
        counts.put("map_edges", edgeCount);
        counts.put("floor_area_polygons", polygonCount);
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
