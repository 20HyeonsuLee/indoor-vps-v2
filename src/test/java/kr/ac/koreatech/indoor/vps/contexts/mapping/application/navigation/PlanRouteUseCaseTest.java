package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.NavigationGraphService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.NodeType;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.Point3;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteEdge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteNode;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteResult;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PlanRouteUseCaseTest {

    private NavigationQueryContext queryContext;
    private NavigationGraphService graphService;
    private GraphQueryFacade graphQueryFacade;
    private PlanRouteUseCase useCase;

    @BeforeEach
    void setUp() {
        queryContext = mock(NavigationQueryContext.class);
        graphService = mock(NavigationGraphService.class);
        graphQueryFacade = mock(GraphQueryFacade.class);
        useCase = new PlanRouteUseCase(queryContext, graphService, graphQueryFacade);
    }

    @Test
    void floorRoute_sameArea_succeeds() {
        UUID floorId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID fromNode = UUID.randomUUID();
        UUID toNode = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();

        FloorScanEntity scanEntity = mock(FloorScanEntity.class);
        var scan = mock(kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.ScanIngestEntity.class);
        when(scan.getScanId()).thenReturn(scanId);
        when(scanEntity.getScan()).thenReturn(scan);

        RouteNode nodeA = new RouteNode(fromNode, NodeType.corridor, new Point3(0, 0, 0), null, areaId);
        RouteNode nodeB = new RouteNode(toNode, NodeType.corridor, new Point3(1, 0, 0), null, areaId);

        when(graphQueryFacade.findNodeArea(fromNode)).thenReturn(Optional.of(areaId));
        when(graphQueryFacade.findNodeArea(toNode)).thenReturn(Optional.of(areaId));
        when(queryContext.activeScanForArea(floorId, Optional.of(areaId))).thenReturn(Optional.of(scanEntity));
        when(graphQueryFacade.routeNodes(scanId)).thenReturn(List.of(nodeA, nodeB));
        when(graphQueryFacade.routeEdges(scanId)).thenReturn(List.of());
        when(graphService.routeBetween(List.of(nodeA, nodeB), List.of(), fromNode, toNode))
                .thenReturn(new RouteResult(List.of(nodeA, nodeB), List.of(), 1.0));

        PlanRouteUseCase.FloorRouteResult result = useCase.floorRoute(new FloorRouteCommand(floorId, fromNode, toNode));

        assertThat(result.scanId()).isEqualTo(scanId);
        assertThat(result.nodes()).hasSize(2);
    }

    @Test
    void floorRoute_differentArea_throwsCrossAreaUnsupported() {
        UUID floorId = UUID.randomUUID();
        UUID fromNode = UUID.randomUUID();
        UUID toNode = UUID.randomUUID();

        when(graphQueryFacade.findNodeArea(fromNode)).thenReturn(Optional.of(UUID.randomUUID()));
        when(graphQueryFacade.findNodeArea(toNode)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThatThrownBy(() -> useCase.floorRoute(new FloorRouteCommand(floorId, fromNode, toNode)))
                .isInstanceOf(ClientApiException.class)
                .satisfies(ex -> {
                    ClientApiException cae = (ClientApiException) ex;
                    assertThat(cae.code()).isEqualTo("CROSS_AREA_ROUTE_UNSUPPORTED");
                    assertThat(cae.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void floorRoute_fromNodeMissing_throwsNotFound() {
        UUID floorId = UUID.randomUUID();
        UUID fromNode = UUID.randomUUID();
        UUID toNode = UUID.randomUUID();

        when(graphQueryFacade.findNodeArea(fromNode)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.floorRoute(new FloorRouteCommand(floorId, fromNode, toNode)))
                .isInstanceOf(ClientApiException.class)
                .satisfies(ex -> {
                    ClientApiException cae = (ClientApiException) ex;
                    assertThat(cae.code()).isEqualTo("ROUTE_NODE_NOT_FOUND");
                    assertThat(cae.statusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void floorRoute_toNodeMissing_throwsNotFound() {
        UUID floorId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID fromNode = UUID.randomUUID();
        UUID toNode = UUID.randomUUID();

        when(graphQueryFacade.findNodeArea(fromNode)).thenReturn(Optional.of(areaId));
        when(graphQueryFacade.findNodeArea(toNode)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.floorRoute(new FloorRouteCommand(floorId, fromNode, toNode)))
                .isInstanceOf(ClientApiException.class)
                .satisfies(ex -> {
                    ClientApiException cae = (ClientApiException) ex;
                    assertThat(cae.code()).isEqualTo("ROUTE_NODE_NOT_FOUND");
                    assertThat(cae.statusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }
}
