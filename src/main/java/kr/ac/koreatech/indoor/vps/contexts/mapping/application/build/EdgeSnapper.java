package kr.ac.koreatech.indoor.vps.contexts.mapping.application.build;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.EdgeType;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.NodeType;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.Point3;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * 고립 corridor 노드를 가장 가까운 sequential edge에 수직 투영(perpendicular foot)으로 snap.
 *
 * <p>foot ∈ (ε, 1-ε): edge 분할 + 새 junction 노드 + 고립→junction edge<br>
 * foot 외부: 가까운 endpoint와 직접 연결
 */
class EdgeSnapper {

    private static final double EPSILON = 0.05;

    private final GeometryFactory geometryFactory;

    EdgeSnapper(GeometryFactory geometryFactory) {
        this.geometryFactory = geometryFactory;
    }

    /**
     * isolated corridors를 snap. edges와 nodes 리스트를 in-place 수정.
     */
    void snap(
            UUID scanId,
            UUID buildJobId,
            UUID areaId,
            Map<Long, MapNodeEntity> corridorById,
            List<MapEdgeEntity> edges,
            List<MapNodeEntity> nodes
    ) {
        List<MapEdgeEntity> sequentialEdges = edges.stream()
                .filter(e -> e.getEdgeType() == EdgeType.rtabmap_link)
                .toList();

        if (sequentialEdges.isEmpty()) {
            return;
        }

        Set<UUID> usedNodeIds = collectUsedNodeIds(sequentialEdges);
        List<MapNodeEntity> isolated = corridorById.values().stream()
                .filter(n -> !usedNodeIds.contains(n.getNodeId()))
                .toList();

        for (MapNodeEntity corridor : isolated) {
            snapSingle(scanId, buildJobId, areaId, corridor, sequentialEdges, edges, nodes,
                    EdgeType.rtabmap_link);
        }
    }

    /**
     * Snap a single POI/connector node onto the nearest current sequential edge via
     * perpendicular foot. Edits {@code edges} and {@code nodes} in-place. The newly
     * added spur edge uses {@code spurType} (e.g. poi_spur for POIs).
     *
     * @return true if a spur was added, false if no sequential edge available.
     */
    boolean snapPoint(
            UUID scanId,
            UUID buildJobId,
            UUID areaId,
            MapNodeEntity node,
            List<MapEdgeEntity> edges,
            List<MapNodeEntity> nodes,
            EdgeType spurType
    ) {
        List<MapEdgeEntity> sequentialEdges = edges.stream()
                .filter(e -> e.getEdgeType() == EdgeType.rtabmap_link)
                .toList();
        if (sequentialEdges.isEmpty()) {
            return false;
        }
        snapSingle(scanId, buildJobId, areaId, node, sequentialEdges, edges, nodes, spurType);
        return true;
    }

    /**
     * 후보 candidateEdges 안에서만 가장 가까운 엣지를 골라 splice. cross-scan
     * fuser용 — 자기 자신을 포함하는 같은-sub-graph 엣지가 더 가깝다고 잡혀
     * self-loop를 만드는 케이스 방지.
     */
    boolean snapPointToCandidates(
            UUID scanId,
            UUID buildJobId,
            UUID areaId,
            MapNodeEntity node,
            List<MapEdgeEntity> candidateEdges,
            List<MapEdgeEntity> edges,
            List<MapNodeEntity> nodes,
            EdgeType spurType
    ) {
        if (candidateEdges == null || candidateEdges.isEmpty()) {
            return false;
        }
        snapSingle(scanId, buildJobId, areaId, node, candidateEdges, edges, nodes, spurType);
        return true;
    }

    private Set<UUID> collectUsedNodeIds(List<MapEdgeEntity> sequentialEdges) {
        Set<UUID> used = new HashSet<>();
        for (MapEdgeEntity e : sequentialEdges) {
            used.add(e.getFromNodeId());
            used.add(e.getToNodeId());
        }
        return used;
    }

    private void snapSingle(
            UUID scanId,
            UUID buildJobId,
            UUID areaId,
            MapNodeEntity corridor,
            List<MapEdgeEntity> sequentialEdges,
            List<MapEdgeEntity> edges,
            List<MapNodeEntity> nodes,
            EdgeType spurType
    ) {
        Point3 c = centerOf(corridor);
        BestProjection best = findBestProjection(c, sequentialEdges, nodes);
        if (best == null) {
            return;
        }

        double t = best.t();
        Point3 foot = best.foot();
        MapEdgeEntity targetEdge = best.edge();

        if (t > EPSILON && t < 1.0 - EPSILON) {
            splitAndConnect(scanId, buildJobId, areaId, corridor, c, foot, t, targetEdge, edges, nodes, spurType);
        } else if (t <= EPSILON) {
            UUID endpointId = targetEdge.getFromNodeId();
            Point3 endpointPos = findNodePos(endpointId, nodes, sequentialEdges);
            edges.add(snapEdge(scanId, buildJobId, areaId, corridor.getNodeId(), endpointId, c, endpointPos, spurType));
        } else {
            UUID endpointId = targetEdge.getToNodeId();
            Point3 endpointPos = findNodePos(endpointId, nodes, sequentialEdges);
            edges.add(snapEdge(scanId, buildJobId, areaId, corridor.getNodeId(), endpointId, c, endpointPos, spurType));
        }
    }

    private BestProjection findBestProjection(
            Point3 c,
            List<MapEdgeEntity> sequentialEdges,
            List<MapNodeEntity> nodes
    ) {
        BestProjection best = null;
        double bestDist = Double.MAX_VALUE;

        for (MapEdgeEntity edge : sequentialEdges) {
            Point3 p = findNodePos(edge.getFromNodeId(), nodes, sequentialEdges);
            Point3 q = findNodePos(edge.getToNodeId(), nodes, sequentialEdges);
            if (p == null || q == null) {
                continue;
            }
            ProjectionResult proj = project(c, p, q);
            if (proj.distance() < bestDist) {
                bestDist = proj.distance();
                best = new BestProjection(edge, proj.t(), proj.foot(), proj.distance());
            }
        }
        return best;
    }

    private Point3 findNodePos(UUID nodeId, List<MapNodeEntity> nodes, List<MapEdgeEntity> edges) {
        for (MapNodeEntity node : nodes) {
            if (node.getNodeId().equals(nodeId)) {
                return centerOf(node);
            }
        }
        return null;
    }

    private ProjectionResult project(Point3 c, Point3 p, Point3 q) {
        double vx = q.x() - p.x();
        double vy = q.y() - p.y();
        double vz = q.z() - p.z();

        double wx = c.x() - p.x();
        double wy = c.y() - p.y();
        double wz = c.z() - p.z();

        double vv = vx * vx + vy * vy + vz * vz;
        double wv = wx * vx + wy * vy + wz * vz;

        double t = (vv == 0.0) ? 0.0 : Math.min(1.0, Math.max(0.0, wv / vv));

        Point3 foot = new Point3(p.x() + t * vx, p.y() + t * vy, p.z() + t * vz);
        double distance = c.distanceTo(foot);
        return new ProjectionResult(t, foot, distance);
    }

    private void splitAndConnect(
            UUID scanId,
            UUID buildJobId,
            UUID areaId,
            MapNodeEntity corridor,
            Point3 c,
            Point3 foot,
            double t,
            MapEdgeEntity targetEdge,
            List<MapEdgeEntity> edges,
            List<MapNodeEntity> nodes,
            EdgeType spurType
    ) {
        UUID junctionId = deterministicUuid(
                "junction:" + scanId + ":" + targetEdge.getEdgeId() + ":" + corridor.getNodeId());
        MapNodeEntity junction = MapNodeEntity.create(
                junctionId, scanId, buildJobId, areaId, NodeType.junction,
                geometryFactory.createPoint(new Coordinate(foot.x(), foot.y(), foot.z())),
                null);
        nodes.add(junction);

        UUID fromId = targetEdge.getFromNodeId();
        UUID toId = targetEdge.getToNodeId();
        double totalLen = targetEdge.getLengthM();

        edges.remove(targetEdge);
        edges.add(seqEdge(scanId, buildJobId, areaId,
                "split-a:" + targetEdge.getEdgeId() + ":" + corridor.getNodeId(),
                fromId, junctionId, foot,
                findNodeCenter(fromId, nodes), t * totalLen));
        edges.add(seqEdge(scanId, buildJobId, areaId,
                "split-b:" + targetEdge.getEdgeId() + ":" + corridor.getNodeId(),
                junctionId, toId, findNodeCenter(toId, nodes),
                foot, (1.0 - t) * totalLen));
        edges.add(snapEdge(scanId, buildJobId, areaId, corridor.getNodeId(), junctionId, c, foot, spurType));
    }

    private Point3 findNodeCenter(UUID nodeId, List<MapNodeEntity> nodes) {
        for (MapNodeEntity node : nodes) {
            if (node.getNodeId().equals(nodeId)) {
                return centerOf(node);
            }
        }
        return null;
    }

    private MapEdgeEntity seqEdge(
            UUID scanId,
            UUID buildJobId,
            UUID areaId,
            String key,
            UUID fromId,
            UUID toId,
            Point3 fromPos,
            Point3 toPos,
            double lengthM
    ) {
        Coordinate fc = new Coordinate(fromPos.x(), fromPos.y(), fromPos.z());
        Coordinate tc = new Coordinate(toPos.x(), toPos.y(), toPos.z());
        return MapEdgeEntity.create(
                deterministicUuid(key + ":" + scanId),
                scanId,
                buildJobId,
                areaId,
                fromId,
                toId,
                EdgeType.rtabmap_link,
                geometryFactory.createLineString(new Coordinate[]{fc, tc}),
                lengthM
        );
    }

    private MapEdgeEntity snapEdge(
            UUID scanId,
            UUID buildJobId,
            UUID areaId,
            UUID fromId,
            UUID toId,
            Point3 fromPos,
            Point3 toPos,
            EdgeType spurType
    ) {
        Coordinate fc = new Coordinate(fromPos.x(), fromPos.y(), fromPos.z());
        Coordinate tc = new Coordinate(toPos.x(), toPos.y(), toPos.z());
        return MapEdgeEntity.create(
                deterministicUuid("snap:" + scanId + ":" + fromId + ":" + toId),
                scanId,
                buildJobId,
                areaId,
                fromId,
                toId,
                spurType,
                geometryFactory.createLineString(new Coordinate[]{fc, tc}),
                fromPos.distanceTo(toPos)
        );
    }

    private Point3 centerOf(MapNodeEntity node) {
        return new Point3(
                node.getGeom().getX(),
                node.getGeom().getY(),
                node.getGeom().getCoordinate().getZ()
        );
    }

    private UUID deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private record ProjectionResult(double t, Point3 foot, double distance) {
    }

    private record BestProjection(MapEdgeEntity edge, double t, Point3 foot, double distance) {
    }
}
