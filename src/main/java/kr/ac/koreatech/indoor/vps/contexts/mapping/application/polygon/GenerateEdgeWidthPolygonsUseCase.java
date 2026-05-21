package kr.ac.koreatech.indoor.vps.contexts.mapping.application.polygon;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.ManualEditScope;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.ManualEditScopeResolver;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaPolygonEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorAreaPolygonRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapEdgeRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapNodeRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * map_edge.width_m 가 설정된 엣지들로부터 corridor strip 형태의
 * floor_area_polygon 을 생성. 같은 area 에 대해 재실행하면
 * mark_session_id = 'edge_width:%' 인 기존 폴리곤을 일소 후 재생성하여
 * manual_edit / corridor-strip 폴리곤과 공존한다.
 *
 * <p>좌표계: map_edge.geom 은 world(x,y,z meters). z 는 두 endpoint 의 평균.
 */
@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class GenerateEdgeWidthPolygonsUseCase {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 0);

    private final MapEdgeRepository edgeRepository;
    private final MapNodeRepository nodeRepository;
    private final FloorAreaPolygonRepository polygonRepository;
    private final ManualEditScopeResolver scopeResolver;

    public GenerateEdgeWidthPolygonsUseCase(
            MapEdgeRepository edgeRepository,
            MapNodeRepository nodeRepository,
            FloorAreaPolygonRepository polygonRepository,
            ManualEditScopeResolver scopeResolver
    ) {
        this.edgeRepository = edgeRepository;
        this.nodeRepository = nodeRepository;
        this.polygonRepository = polygonRepository;
        this.scopeResolver = scopeResolver;
    }

    @Transactional
    public Result generate(UUID areaId) {
        ManualEditScope scope = scopeResolver.resolve(areaId);
        int wiped = polygonRepository.deleteEdgeWidthByAreaId(areaId);
        List<MapEdgeEntity> edges = edgeRepository.findByAreaIdAndStaleFalseOrderByEdgeId(areaId);
        List<MapNodeEntity> nodes = nodeRepository.findByAreaIdAndStaleFalseOrderByNodeId(areaId);
        Map<UUID, MapNodeEntity> nodeById = new HashMap<>();
        for (MapNodeEntity n : nodes) {
            nodeById.put(n.getNodeId(), n);
        }
        int created = 0;
        int skippedNoWidth = 0;
        int skippedDegenerate = 0;
        for (MapEdgeEntity edge : edges) {
            Double width = edge.getWidthM();
            if (width == null || width <= 0.0) {
                skippedNoWidth++;
                continue;
            }
            MapNodeEntity fromNode = nodeById.get(edge.getFromNodeId());
            MapNodeEntity toNode = nodeById.get(edge.getToNodeId());
            if (fromNode == null || toNode == null) {
                skippedDegenerate++;
                continue;
            }
            Polygon strip = stripFromNodes(fromNode.getGeom(), toNode.getGeom(), width);
            if (strip == null) {
                skippedDegenerate++;
                continue;
            }
            FloorAreaPolygonEntity poly = FloorAreaPolygonEntity.create(
                    deterministicUuid("edge_width_polygon:" + edge.getEdgeId()),
                    scope.scanId(),
                    scope.buildJobId(),
                    scope.area().getFloor(),
                    scope.area(),
                    "edge_width:" + edge.getEdgeId(),
                    strip,
                    List.of()
            );
            polygonRepository.save(poly);
            created++;
        }
        polygonRepository.flush();
        return new Result(wiped, created, skippedNoWidth, skippedDegenerate, edges.size());
    }

    private Polygon stripFromNodes(Point from, Point to, double width) {
        if (from == null || to == null) return null;
        double ax = from.getX();
        double ay = from.getY();
        double az = Double.isNaN(from.getCoordinate().z) ? 0.0 : from.getCoordinate().z;
        double bx = to.getX();
        double by = to.getY();
        double bz = Double.isNaN(to.getCoordinate().z) ? 0.0 : to.getCoordinate().z;
        double dx = bx - ax;
        double dy = by - ay;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6) return null;
        double ux = dx / len;
        double uy = dy / len;
        double nx = -uy;
        double ny = ux;
        double half = width / 2.0;
        double z = (az + bz) / 2.0;
        Coordinate c1 = new Coordinate(ax - nx * half, ay - ny * half, z);
        Coordinate c2 = new Coordinate(bx - nx * half, by - ny * half, z);
        Coordinate c3 = new Coordinate(bx + nx * half, by + ny * half, z);
        Coordinate c4 = new Coordinate(ax + nx * half, ay + ny * half, z);
        LinearRing ring = GF.createLinearRing(new Coordinate[] {c1, c2, c3, c4, c1});
        return GF.createPolygon(ring);
    }

    private UUID deterministicUuid(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    public record Result(int wiped, int created, int skippedNoWidth, int skippedDegenerate, int totalEdges) {
    }
}
