package kr.ac.koreatech.indoor.vps.application.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.api.ClientApiException;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.DbEnums.EdgeType;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.DbEnums.NodeType;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.MapNodeEntity;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.http.HttpStatus;

class NavigationGraphServiceTest {
    private static final UUID SCAN_ID = id("scan");
    private static final UUID BUILD_JOB_ID = id("build-job");

    private final NavigationGraphService service = new NavigationGraphService();
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Test
    void shortestPathUsesWeightedGraphLibrary() {
        MapNodeEntity a = node("a", 0, 0, 0);
        MapNodeEntity b = node("b", 1, 0, 0);
        MapNodeEntity c = node("c", 2, 0, 0);
        MapEdgeEntity ab = edge("ab", a, b, 1.0);
        MapEdgeEntity bc = edge("bc", b, c, 1.0);
        MapEdgeEntity ac = edge("ac", a, c, 5.0);

        NavigationGraphService.RouteResult result = service.routeBetween(
                List.of(a, b, c),
                List.of(ab, bc, ac),
                a.getNodeId(),
                c.getNodeId()
        );

        assertThat(result.nodes()).extracting(MapNodeEntity::getNodeId)
                .containsExactly(a.getNodeId(), b.getNodeId(), c.getNodeId());
        assertThat(result.edges()).extracting(NavigationGraphService.RouteEdge::id)
                .containsExactly(ab.getEdgeId(), bc.getEdgeId());
        assertThat(result.totalDistance()).isEqualTo(2.0);
    }

    @Test
    void reverseTraversalFlipsRouteEdgeDirection() {
        MapNodeEntity a = node("a", 0, 0, 0);
        MapNodeEntity b = node("b", 1, 0, 0);
        MapNodeEntity c = node("c", 2, 0, 0);

        NavigationGraphService.RouteResult result = service.routeBetween(
                List.of(a, b, c),
                List.of(edge("ab", a, b, 1.0), edge("bc", b, c, 1.0)),
                c.getNodeId(),
                a.getNodeId()
        );

        assertThat(result.nodes()).extracting(MapNodeEntity::getNodeId)
                .containsExactly(c.getNodeId(), b.getNodeId(), a.getNodeId());
        assertThat(result.edges()).extracting(NavigationGraphService.RouteEdge::fromId)
                .containsExactly(c.getNodeId(), b.getNodeId());
        assertThat(result.edges()).extracting(NavigationGraphService.RouteEdge::toId)
                .containsExactly(b.getNodeId(), a.getNodeId());
        assertThat(result.totalDistance()).isEqualTo(2.0);
    }

    @Test
    void unreachableRouteReturnsEmptyResult() {
        MapNodeEntity a = node("a", 0, 0, 0);
        MapNodeEntity b = node("b", 1, 0, 0);
        MapNodeEntity c = node("c", 100, 0, 0);

        NavigationGraphService.RouteResult result = service.routeBetween(
                List.of(a, b, c),
                List.of(edge("ab", a, b, 1.0)),
                a.getNodeId(),
                c.getNodeId()
        );

        assertThat(result.nodes()).isEmpty();
        assertThat(result.edges()).isEmpty();
        assertThat(result.totalDistance()).isZero();
    }

    @Test
    void missingRouteNodeThrowsTypedApiError() {
        MapNodeEntity a = node("a", 0, 0, 0);

        assertThatThrownBy(() -> service.routeBetween(
                List.of(a),
                List.of(),
                a.getNodeId(),
                id("missing")
        ))
                .isInstanceOfSatisfying(ClientApiException.class, error -> {
                    assertThat(error.statusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(error.code()).isEqualTo("ROUTE_NODE_NOT_FOUND");
                });
    }

    @Test
    void duplicateEdgesKeepShortestWeightAndRouteEdge() {
        MapNodeEntity a = node("a", 0, 0, 0);
        MapNodeEntity b = node("b", 1, 0, 0);
        MapNodeEntity c = node("c", 2, 0, 0);
        MapEdgeEntity longAb = edge("ab-long", a, b, 10.0);
        MapEdgeEntity shortAb = edge("ab-short", a, b, 2.0);
        MapEdgeEntity bc = edge("bc", b, c, 1.0);

        NavigationGraphService.RouteResult result = service.routeBetween(
                List.of(a, b, c),
                List.of(longAb, shortAb, bc),
                a.getNodeId(),
                c.getNodeId()
        );

        assertThat(result.totalDistance()).isEqualTo(3.0);
        assertThat(result.edges()).extracting(NavigationGraphService.RouteEdge::id)
                .containsExactly(shortAb.getEdgeId(), bc.getEdgeId());
    }

    @Test
    void nearestNodeUsesZDistanceWhenPresent() {
        MapNodeEntity sameXyDifferentZ = node("low", 0, 0, 0);
        MapNodeEntity closerIn3d = node("high", 1, 0, 10);

        UUID nearest = service.nearestNode(List.of(sameXyDifferentZ, closerIn3d), 0, 0, 10);

        assertThat(nearest).isEqualTo(closerIn3d.getNodeId());
    }

    private MapNodeEntity node(String name, double x, double y, double z) {
        return MapNodeEntity.create(
                id("node-" + name),
                SCAN_ID,
                BUILD_JOB_ID,
                NodeType.junction,
                geometryFactory.createPoint(new Coordinate(x, y, z)),
                name
        );
    }

    private MapEdgeEntity edge(String name, MapNodeEntity from, MapNodeEntity to, double lengthM) {
        Coordinate[] coordinates = new Coordinate[] {
                from.getGeom().getCoordinate(),
                to.getGeom().getCoordinate()
        };
        return MapEdgeEntity.create(
                id("edge-" + name),
                SCAN_ID,
                BUILD_JOB_ID,
                from.getNodeId(),
                to.getNodeId(),
                EdgeType.skeleton,
                geometryFactory.createLineString(coordinates),
                lengthM
        );
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
