package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.BuildingRouteGraphProvider.CompositeGraph;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.NodeType;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.Point3;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteEdge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteNode;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorScanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BuildingRouteGraphProviderTest {

    private BuildingRouteGraphProvider provider;
    private FloorScanRepository floorScanRepository;
    private GraphQueryFacade graphQueryFacade;

    @BeforeEach
    void setUp() {
        floorScanRepository = mock(FloorScanRepository.class);
        graphQueryFacade = mock(GraphQueryFacade.class);
        provider = new BuildingRouteGraphProvider(floorScanRepository, graphQueryFacade);
    }

    @Test
    void isCrossArea_returnsFalseWhenSameArea() {
        UUID areaId = UUID.randomUUID();
        UUID nodeA = UUID.randomUUID();
        UUID nodeB = UUID.randomUUID();

        RouteNode a = new RouteNode(nodeA, NodeType.corridor, new Point3(0, 0, 0), null, areaId);
        RouteNode b = new RouteNode(nodeB, NodeType.corridor, new Point3(1, 0, 0), null, areaId);

        CompositeGraph graph = new CompositeGraph(List.of(a, b), List.of());

        assertThat(graph.isCrossArea(nodeA, nodeB)).isFalse();
    }

    @Test
    void isCrossArea_returnsTrueWhenDifferentArea() {
        UUID area1 = UUID.randomUUID();
        UUID area2 = UUID.randomUUID();
        UUID nodeA = UUID.randomUUID();
        UUID nodeB = UUID.randomUUID();

        RouteNode a = new RouteNode(nodeA, NodeType.corridor, new Point3(0, 0, 0), null, area1);
        RouteNode b = new RouteNode(nodeB, NodeType.corridor, new Point3(1, 0, 0), null, area2);

        CompositeGraph graph = new CompositeGraph(List.of(a, b), List.of());

        assertThat(graph.isCrossArea(nodeA, nodeB)).isTrue();
    }

    @Test
    void invalidate_removesCache() {
        UUID buildingId = UUID.randomUUID();
        when(floorScanRepository.findActiveForBuilding(buildingId)).thenReturn(List.of());

        CompositeGraph first = provider.getOrBuild(buildingId);
        provider.invalidate(buildingId);
        CompositeGraph second = provider.getOrBuild(buildingId);

        assertThat(first).isNotSameAs(second);
    }
}
