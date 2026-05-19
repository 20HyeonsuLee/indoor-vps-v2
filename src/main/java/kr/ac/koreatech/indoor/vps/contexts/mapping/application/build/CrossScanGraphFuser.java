package kr.ac.koreatech.indoor.vps.contexts.mapping.application.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.EdgeType;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.NodeType;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.Point3;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 머지된 multi-scan 데이터에서 각 sub-scan의 corridor sub-graph가 sequential
 * edge로만 잇닿아 있어 sub-graph 간 분리가 발생. 클라이언트는 인접 청크가
 * 시각적으로 겹치게 스캔하므로, 같은 물리 위치에 있는 두 sub-graph의 노드 또는
 * 다른 sub-graph의 엣지에 매우 가까운 노드를 합쳐 통합 그래프로 만든다.
 *
 *  - 노드 ↔ 노드 거리 ≤ mergeRadius (≈1m): 두 노드를 하나로 통합. follower의
 *    edge 참조를 leader 의 nodeId로 재작성하고 follower를 nodes에서 제거.
 *  - 노드 ↔ 엣지 수선의 발 ≤ spliceRadius (≈2m): EdgeSnapper.snapPoint로
 *    엣지를 둘로 쪼개고 junction을 통해 corridor 노드와 잇는다.
 *
 * 분리된 connected component만 처리하므로 같은 sub-graph 내 인접 노드는
 * 영향을 주지 않는다 (over-fusion 방지).
 */
public class CrossScanGraphFuser {
    private static final Logger log = LoggerFactory.getLogger(CrossScanGraphFuser.class);

    private final EdgeSnapper edgeSnapper;

    public CrossScanGraphFuser(EdgeSnapper edgeSnapper) {
        this.edgeSnapper = edgeSnapper;
    }

    void fuse(
            UUID scanId,
            UUID buildJobId,
            UUID areaId,
            List<MapNodeEntity> nodes,
            List<MapEdgeEntity> edges,
            double mergeRadius,
            double spliceRadius
    ) {
        int maxIter = 64;
        int merges = 0;
        int splices = 0;
        for (int iter = 0; iter < maxIter; iter++) {
            List<MapNodeEntity> corridors = nodes.stream()
                    .filter(n -> n.getNodeType() == NodeType.corridor)
                    .toList();
            if (corridors.size() < 2) {
                break;
            }
            UnionFind uf = buildComponents(corridors, edges);
            if (uf.componentCount() < 2) {
                break;
            }
            Best best = findBestCrossComponent(nodes, corridors, edges, uf, mergeRadius, spliceRadius);
            if (best == null) {
                break;
            }
            if (best.merge) {
                mergeNodes(nodes, edges, best.leader, best.follower);
                merges++;
            } else {
                // cross-component candidate 목록을 명시 전달 — 자기 자신 endpoint
                // 인 같은-component edge가 0거리로 잡혀 self-loop 만드는 버그 방지.
                edgeSnapper.snapPointToCandidates(scanId, buildJobId, areaId, best.node,
                        best.spliceCandidates, edges, nodes, EdgeType.rtabmap_link);
                splices++;
            }
        }
        // EdgeSnapper.splitAndConnect 호출 누적으로 zero-length self-loop이 남을 수
        // 있어 후처리로 정리. node merge 단계는 이미 self-loop를 제거하지만 splice
        // 경로는 EdgeSnapper 내부 호출이라 별도 cleanup.
        int dropped = 0;
        List<MapEdgeEntity> toDrop = new ArrayList<>();
        for (MapEdgeEntity e : edges) {
            if (e.getEdgeType() == EdgeType.rtabmap_link
                    && (e.getFromNodeId().equals(e.getToNodeId()) || e.getLengthM() < 1e-3)) {
                toDrop.add(e);
            }
        }
        edges.removeAll(toDrop);
        dropped = toDrop.size();

        if (merges > 0 || splices > 0 || dropped > 0) {
            log.info("[cross-scan-fuser] merges={} splices={} self_loops_dropped={}",
                    merges, splices, dropped);
        }
    }

    private UnionFind buildComponents(List<MapNodeEntity> corridors, List<MapEdgeEntity> edges) {
        Map<UUID, Integer> idx = new HashMap<>();
        for (int i = 0; i < corridors.size(); i++) {
            idx.put(corridors.get(i).getNodeId(), i);
        }
        UnionFind uf = new UnionFind(corridors.size());
        for (MapEdgeEntity e : edges) {
            if (e.getEdgeType() != EdgeType.rtabmap_link) {
                continue;
            }
            Integer a = idx.get(e.getFromNodeId());
            Integer b = idx.get(e.getToNodeId());
            if (a == null || b == null) {
                continue;
            }
            uf.union(a, b);
        }
        return uf;
    }

    private Best findBestCrossComponent(
            List<MapNodeEntity> allNodes,
            List<MapNodeEntity> corridors,
            List<MapEdgeEntity> edges,
            UnionFind uf,
            double mergeRadius,
            double spliceRadius
    ) {
        // node-node 후보 — merge가 우선이라 mergeRadius 까지만
        Best bestMerge = null;
        for (int i = 0; i < corridors.size(); i++) {
            for (int j = i + 1; j < corridors.size(); j++) {
                if (uf.find(i) == uf.find(j)) {
                    continue;
                }
                Point3 a = centerOf(corridors.get(i));
                Point3 b = centerOf(corridors.get(j));
                double d = a.distanceTo(b);
                if (d >= mergeRadius) {
                    continue;
                }
                if (bestMerge == null || d < bestMerge.distance) {
                    bestMerge = new Best();
                    bestMerge.merge = true;
                    bestMerge.distance = d;
                    bestMerge.leader = corridors.get(i);
                    bestMerge.follower = corridors.get(j);
                }
            }
        }
        if (bestMerge != null) {
            return bestMerge;
        }

        // node-edge 후보 — 모든 노드 × 모든 엣지에 대해 수선의 발 검사.
        // splice 규칙: 노드가 엣지의 endpoint가 아니고, 수선의 발이 segment
        // 내부에 있고(t ∈ (ε, 1-ε)), perpendicular 거리 ≤ spliceRadius이면 splice.
        // component 검사는 제외 — 같은 component 안에서도 엣지 위에 떨어진
        // 노드는 splice (T-junction 형성).
        // endpoint geometry lookup은 전체 nodes 기준 — junction/POI 가 endpoint 인
        // rtabmap_link 도 splice 대상이어야 함 (corridor 만 보면 node merge 이후
        // junction 으로 변환된 엣지들이 누락돼 components 가 영구 분리됨).
        Map<UUID, Point3> nodePos = new HashMap<>();
        for (MapNodeEntity n : allNodes) {
            nodePos.put(n.getNodeId(), centerOf(n));
        }
        Best bestSplice = null;
        for (MapNodeEntity n : corridors) {
            Point3 c = centerOf(n);
            List<MapEdgeEntity> candidates = new ArrayList<>();
            double nearestDist = Double.POSITIVE_INFINITY;
            for (MapEdgeEntity e : edges) {
                if (e.getEdgeType() != EdgeType.rtabmap_link) continue;
                // 자기 자신이 endpoint인 엣지는 skip — splice 의미 없고 self-loop 위험.
                if (e.getFromNodeId().equals(n.getNodeId())
                        || e.getToNodeId().equals(n.getNodeId())) continue;
                Point3 p = nodePos.get(e.getFromNodeId());
                Point3 q = nodePos.get(e.getToNodeId());
                if (p == null || q == null) continue;
                // segment 내부에 foot 있는 경우만 (endpoint extrapolation 제외).
                if (!footWithinSegment(c, p, q)) continue;
                double d = perpendicularDistance(c, p, q);
                if (d >= spliceRadius) continue;
                candidates.add(e);
                if (d < nearestDist) {
                    nearestDist = d;
                }
            }
            if (!candidates.isEmpty() && (bestSplice == null || nearestDist < bestSplice.distance)) {
                bestSplice = new Best();
                bestSplice.merge = false;
                bestSplice.distance = nearestDist;
                bestSplice.node = n;
                bestSplice.spliceCandidates = candidates;
            }
        }
        return bestSplice;
    }

    private static boolean footWithinSegment(Point3 c, Point3 p, Point3 q) {
        double vx = q.x() - p.x(), vy = q.y() - p.y(), vz = q.z() - p.z();
        double wx = c.x() - p.x(), wy = c.y() - p.y(), wz = c.z() - p.z();
        double vv = vx * vx + vy * vy + vz * vz;
        if (vv == 0.0) return false;
        double wv = wx * vx + wy * vy + wz * vz;
        double t = wv / vv;
        return t > 0.02 && t < 0.98;
    }

    private Integer indexOfNode(List<MapNodeEntity> corridors, MapNodeEntity n) {
        for (int i = 0; i < corridors.size(); i++) {
            if (corridors.get(i) == n) return i;
        }
        return null;
    }

    private Integer indexOfNodeId(List<MapNodeEntity> corridors, UUID id) {
        for (int i = 0; i < corridors.size(); i++) {
            if (corridors.get(i).getNodeId().equals(id)) return i;
        }
        return null;
    }

    private void mergeNodes(
            List<MapNodeEntity> nodes,
            List<MapEdgeEntity> edges,
            MapNodeEntity leader,
            MapNodeEntity follower
    ) {
        UUID leaderId = leader.getNodeId();
        UUID followerId = follower.getNodeId();
        List<MapEdgeEntity> toRemove = new ArrayList<>();
        for (MapEdgeEntity e : edges) {
            boolean changedFrom = e.getFromNodeId().equals(followerId);
            boolean changedTo = e.getToNodeId().equals(followerId);
            if (!changedFrom && !changedTo) continue;
            if (changedFrom) e.changeFromNodeId(leaderId);
            if (changedTo) e.changeToNodeId(leaderId);
            // self-loop가 됐으면 제거
            if (e.getFromNodeId().equals(e.getToNodeId())) {
                toRemove.add(e);
            }
        }
        edges.removeAll(toRemove);
        nodes.remove(follower);
    }

    private static Point3 centerOf(MapNodeEntity node) {
        Point p = (Point) node.getGeom();
        Coordinate c = p.getCoordinate();
        double z = Double.isNaN(c.getZ()) ? 0.0 : c.getZ();
        return new Point3(c.getX(), c.getY(), z);
    }

    private static double perpendicularDistance(Point3 c, Point3 p, Point3 q) {
        double vx = q.x() - p.x(), vy = q.y() - p.y(), vz = q.z() - p.z();
        double wx = c.x() - p.x(), wy = c.y() - p.y(), wz = c.z() - p.z();
        double vv = vx * vx + vy * vy + vz * vz;
        if (vv == 0.0) return c.distanceTo(p);
        double wv = wx * vx + wy * vy + wz * vz;
        double t = Math.max(0.0, Math.min(1.0, wv / vv));
        Point3 foot = new Point3(p.x() + t * vx, p.y() + t * vy, p.z() + t * vz);
        return c.distanceTo(foot);
    }

    private static final class Best {
        boolean merge;
        double distance;
        MapNodeEntity leader;
        MapNodeEntity follower;
        MapNodeEntity node;
        List<MapEdgeEntity> spliceCandidates;
    }

    private static final class UnionFind {
        private final int[] parent;

        UnionFind(int n) {
            parent = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;
        }

        int find(int x) {
            while (parent[x] != x) {
                parent[x] = parent[parent[x]];
                x = parent[x];
            }
            return x;
        }

        void union(int a, int b) {
            int ra = find(a), rb = find(b);
            if (ra != rb) parent[ra] = rb;
        }

        int componentCount() {
            int n = 0;
            for (int i = 0; i < parent.length; i++) {
                if (parent[i] == i) n++;
            }
            return n;
        }
    }
}
