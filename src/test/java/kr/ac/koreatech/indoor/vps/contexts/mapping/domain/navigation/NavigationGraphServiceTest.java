package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.common.DomainException;
import org.junit.jupiter.api.Test;

class NavigationGraphServiceTest {
    private final NavigationGraphService service = new NavigationGraphService();

    @Test
    void shortestPathUsesWeightedGraphLibrary() {
        RouteNode a = node("a", 0, 0, 0);
        RouteNode b = node("b", 1, 0, 0);
        RouteNode c = node("c", 2, 0, 0);
        RouteEdge ab = edge("ab", a, b, 1.0);
        RouteEdge bc = edge("bc", b, c, 1.0);
        RouteEdge ac = edge("ac", a, c, 5.0);

        RouteResult result = service.routeBetween(
                List.of(a, b, c),
                List.of(ab, bc, ac),
                a.id(),
                c.id()
        );

        assertThat(result.nodes()).extracting(RouteNode::id)
                .containsExactly(a.id(), b.id(), c.id());
        assertThat(result.edges()).extracting(RouteEdge::id)
                .containsExactly(ab.id(), bc.id());
        assertThat(result.totalDistance()).isEqualTo(2.0);
    }

    @Test
    void reverseTraversalFlipsRouteEdgeDirection() {
        RouteNode a = node("a", 0, 0, 0);
        RouteNode b = node("b", 1, 0, 0);
        RouteNode c = node("c", 2, 0, 0);

        RouteResult result = service.routeBetween(
                List.of(a, b, c),
                List.of(edge("ab", a, b, 1.0), edge("bc", b, c, 1.0)),
                c.id(),
                a.id()
        );

        assertThat(result.nodes()).extracting(RouteNode::id)
                .containsExactly(c.id(), b.id(), a.id());
        assertThat(result.edges()).extracting(RouteEdge::fromId)
                .containsExactly(c.id(), b.id());
        assertThat(result.edges()).extracting(RouteEdge::toId)
                .containsExactly(b.id(), a.id());
        assertThat(result.totalDistance()).isEqualTo(2.0);
    }

    @Test
    void unreachableRouteReturnsEmptyResult() {
        RouteNode a = node("a", 0, 0, 0);
        RouteNode b = node("b", 1, 0, 0);
        RouteNode c = node("c", 100, 0, 0);

        RouteResult result = service.routeBetween(
                List.of(a, b, c),
                List.of(edge("ab", a, b, 1.0)),
                a.id(),
                c.id()
        );

        assertThat(result.nodes()).isEmpty();
        assertThat(result.edges()).isEmpty();
        assertThat(result.totalDistance()).isZero();
    }

    @Test
    void missingRouteNodeThrowsTypedApiError() {
        RouteNode a = node("a", 0, 0, 0);

        assertThatThrownBy(() -> service.routeBetween(
                List.of(a),
                List.of(),
                a.id(),
                id("missing")
        ))
                .isInstanceOfSatisfying(DomainException.class, error -> {
                    assertThat(error.status()).isEqualTo(404);
                    assertThat(error.code()).isEqualTo("ROUTE_NODE_NOT_FOUND");
                });
    }

    @Test
    void duplicateEdgesKeepShortestWeightAndRouteEdge() {
        RouteNode a = node("a", 0, 0, 0);
        RouteNode b = node("b", 1, 0, 0);
        RouteNode c = node("c", 2, 0, 0);
        RouteEdge longAb = edge("ab-long", a, b, 10.0);
        RouteEdge shortAb = edge("ab-short", a, b, 2.0);
        RouteEdge bc = edge("bc", b, c, 1.0);

        RouteResult result = service.routeBetween(
                List.of(a, b, c),
                List.of(longAb, shortAb, bc),
                a.id(),
                c.id()
        );

        assertThat(result.totalDistance()).isEqualTo(3.0);
        assertThat(result.edges()).extracting(RouteEdge::id)
                .containsExactly(shortAb.id(), bc.id());
    }

    @Test
    void nearestNodeUsesZDistanceWhenPresent() {
        RouteNode sameXyDifferentZ = node("low", 0, 0, 0);
        RouteNode closerIn3d = node("high", 1, 0, 10);

        UUID nearest = service.nearestNode(List.of(sameXyDifferentZ, closerIn3d), new Point3(0, 0, 10));

        assertThat(nearest).isEqualTo(closerIn3d.id());
    }

    private RouteNode node(String name, double x, double y, double z) {
        return new RouteNode(
                id("node-" + name),
                NodeType.junction,
                new Point3(x, y, z),
                name
        );
    }

    private RouteEdge edge(String name, RouteNode from, RouteNode to, double lengthM) {
        return new RouteEdge(
                id("edge-" + name),
                from.id(),
                to.id(),
                lengthM,
                EdgeType.rtabmap_link
        );
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
