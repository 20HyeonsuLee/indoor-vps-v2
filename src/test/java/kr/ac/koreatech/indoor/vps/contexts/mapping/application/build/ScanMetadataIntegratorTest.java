package kr.ac.koreatech.indoor.vps.contexts.mapping.application.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.build.ScanMetadataIntegrator.IntegrationResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.EdgeType;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.NodeType;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildingRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.VerticalConnectorRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.VerticalConnectorStopRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.BranchEdgeRow;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.BranchMarkRow;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.InterfloorMarkRow;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.KeyframeRow;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.PoiMarkRow;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.ScanMetadata;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.SessionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScanMetadataIntegratorTest {

    private ScanMetadataIntegrator integrator;

    @BeforeEach
    void setUp() {
        BuildingRepository buildingRepository = mock(BuildingRepository.class);
        FloorRepository floorRepository = mock(FloorRepository.class);
        VerticalConnectorRepository verticalConnectorRepository = mock(VerticalConnectorRepository.class);
        VerticalConnectorStopRepository verticalConnectorStopRepository = mock(VerticalConnectorStopRepository.class);

        when(buildingRepository.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        when(floorRepository.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());

        integrator = new ScanMetadataIntegrator(
                buildingRepository,
                floorRepository,
                verticalConnectorRepository,
                verticalConnectorStopRepository
        );
    }

    @Test
    void corridorMarksBecomMapNodes() {
        UUID scanId = UUID.randomUUID();
        UUID buildJobId = UUID.randomUUID();
        SessionInfo session = new SessionInfo(null, null, null, null, null);

        BranchMarkRow corridor1 = new BranchMarkRow(1L, 1, "corridor", 0.0, 0.0, 0.0, null, null, null);
        BranchMarkRow corridor2 = new BranchMarkRow(2L, 2, "corridor", 1.0, 0.0, 0.0, null, null, null);
        BranchMarkRow corner = new BranchMarkRow(3L, 3, "corner", 0.5, 0.5, 0.0, null, null, 10L);

        ScanMetadata metadata = new ScanMetadata(
                session,
                List.of(),
                List.of(corridor1, corridor2, corner),
                List.of(),
                List.of(),
                List.of()
        );

        IntegrationResult result = integrator.integrate(scanId, buildJobId, metadata);

        assertThat(result.nodes()).hasSize(2);
        assertThat(result.nodes()).allMatch(n -> n.getNodeType() == NodeType.corridor);
    }

    @Test
    void cornerMarksBecomPolygonNotNode() {
        UUID scanId = UUID.randomUUID();
        UUID buildJobId = UUID.randomUUID();
        SessionInfo session = new SessionInfo(null, null, null, null, null);

        BranchMarkRow c1 = new BranchMarkRow(1L, 1, "corner", 0.0, 0.0, 0.0, null, null, 5L);
        BranchMarkRow c2 = new BranchMarkRow(2L, 2, "corner", 1.0, 0.0, 0.0, null, null, 5L);
        BranchMarkRow c3 = new BranchMarkRow(3L, 3, "corner", 1.0, 1.0, 0.0, null, null, 5L);
        BranchMarkRow c4 = new BranchMarkRow(4L, 4, "corner", 0.0, 1.0, 0.0, null, null, 5L);

        BranchEdgeRow polygonEdge = new BranchEdgeRow(10L, 1L, 4L, "cornerPolygon");

        ScanMetadata metadata = new ScanMetadata(
                session,
                List.of(),
                List.of(c1, c2, c3, c4),
                List.of(polygonEdge),
                List.of(),
                List.of()
        );

        IntegrationResult result = integrator.integrate(scanId, buildJobId, metadata);

        assertThat(result.nodes()).isEmpty();
        assertThat(result.polygons()).hasSize(1);
        assertThat(result.polygons().getFirst().getMarkSessionId()).isEqualTo("5");
        assertThat(result.polygons().getFirst().getSourceMarkIds()).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void sequentialEdgesBecomMapEdges() {
        UUID scanId = UUID.randomUUID();
        UUID buildJobId = UUID.randomUUID();
        SessionInfo session = new SessionInfo(null, null, null, null, null);

        BranchMarkRow corridor1 = new BranchMarkRow(1L, 1, "corridor", 0.0, 0.0, 0.0, null, null, null);
        BranchMarkRow corridor2 = new BranchMarkRow(2L, 2, "corridor", 1.0, 0.0, 0.0, null, null, null);
        BranchEdgeRow seqEdge = new BranchEdgeRow(1L, 1L, 2L, "sequential");

        ScanMetadata metadata = new ScanMetadata(
                session,
                List.of(),
                List.of(corridor1, corridor2),
                List.of(seqEdge),
                List.of(),
                List.of()
        );

        IntegrationResult result = integrator.integrate(scanId, buildJobId, metadata);

        assertThat(result.edges()).hasSize(1);
        assertThat(result.edges().getFirst().getEdgeType()).isEqualTo(EdgeType.rtabmap_link);
    }

    @Test
    void cornerPolygonEdgesAreIgnoredAsMapEdges() {
        UUID scanId = UUID.randomUUID();
        UUID buildJobId = UUID.randomUUID();
        SessionInfo session = new SessionInfo(null, null, null, null, null);

        BranchMarkRow c1 = new BranchMarkRow(1L, 1, "corner", 0.0, 0.0, 0.0, null, null, 7L);
        BranchMarkRow c2 = new BranchMarkRow(2L, 2, "corner", 1.0, 0.0, 0.0, null, null, 7L);
        BranchMarkRow c3 = new BranchMarkRow(3L, 3, "corner", 0.5, 1.0, 0.0, null, null, 7L);
        BranchEdgeRow polygonEdge = new BranchEdgeRow(1L, 1L, 3L, "cornerPolygon");

        ScanMetadata metadata = new ScanMetadata(
                session,
                List.of(),
                List.of(c1, c2, c3),
                List.of(polygonEdge),
                List.of(),
                List.of()
        );

        IntegrationResult result = integrator.integrate(scanId, buildJobId, metadata);

        assertThat(result.edges()).isEmpty();
    }

    @Test
    void poiMarkCreatesNodeAndSpurEdgeToNearestCorridor() {
        UUID scanId = UUID.randomUUID();
        UUID buildJobId = UUID.randomUUID();
        SessionInfo session = new SessionInfo(null, null, null, null, null);

        BranchMarkRow corridor = new BranchMarkRow(1L, 1, "corridor", 0.0, 0.0, 0.0, null, null, null);
        PoiMarkRow poi = new PoiMarkRow(1L, 2, 0.3, 0.0, 0.0, "강의실 101");

        ScanMetadata metadata = new ScanMetadata(
                session,
                List.of(),
                List.of(corridor),
                List.of(),
                List.of(poi),
                List.of()
        );

        IntegrationResult result = integrator.integrate(scanId, buildJobId, metadata);

        long corridorNodes = result.nodes().stream()
                .filter(n -> n.getNodeType() == NodeType.corridor).count();
        long poiNodes = result.nodes().stream()
                .filter(n -> n.getNodeType() == NodeType.poi).count();
        assertThat(corridorNodes).isEqualTo(1);
        assertThat(poiNodes).isEqualTo(1);
        assertThat(result.edges()).hasSize(1);
        assertThat(result.edges().getFirst().getEdgeType()).isEqualTo(EdgeType.poi_spur);
    }

    @Test
    void emptyMetadataProducesEmptyResult() {
        UUID scanId = UUID.randomUUID();
        UUID buildJobId = UUID.randomUUID();
        SessionInfo session = new SessionInfo(null, null, null, null, null);

        ScanMetadata metadata = new ScanMetadata(
                session, List.of(), List.of(), List.of(), List.of(), List.of());

        IntegrationResult result = integrator.integrate(scanId, buildJobId, metadata);

        assertThat(result.nodes()).isEmpty();
        assertThat(result.edges()).isEmpty();
        assertThat(result.polygons()).isEmpty();
        assertThat(result.pois()).isEmpty();
    }
}
