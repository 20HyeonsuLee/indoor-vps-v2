package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaPolygonEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.PoiCanonicalEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorStopEntity;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

final class FloorMapEtag {

    private FloorMapEtag() {
    }

    static String compute(Input in) {
        int h = Objects.hash(in.floorId, in.scanId, in.buildJobId,
                nodesHash(in.nodes), edgesHash(in.edges),
                polygonsHash(in.polygons), poisHash(in.pois), stopsHash(in.stops));
        return Integer.toHexString(h);
    }

    record Input(
            UUID floorId, UUID scanId, UUID buildJobId,
            List<MapNodeEntity> nodes,
            List<MapEdgeEntity> edges,
            List<FloorAreaPolygonEntity> polygons,
            List<PoiCanonicalEntity> pois,
            List<VerticalConnectorStopEntity> stops) {
    }

    private static int nodesHash(List<MapNodeEntity> nodes) {
        int h = nodes.size();
        for (MapNodeEntity n : nodes) {
            h = 31 * h + Objects.hash(n.getNodeId(), n.getNodeType(), n.getLabel(),
                    pointHash(n.getGeom()), originOf(n.getSourceRef()));
        }
        return h;
    }

    private static int edgesHash(List<MapEdgeEntity> edges) {
        int h = edges.size();
        for (MapEdgeEntity e : edges) {
            h = 31 * h + Objects.hash(e.getEdgeId(), e.getFromNodeId(), e.getToNodeId(),
                    e.getEdgeType(), Double.hashCode(e.getLengthM()));
        }
        return h;
    }

    private static int polygonsHash(List<FloorAreaPolygonEntity> polygons) {
        int h = polygons.size();
        for (FloorAreaPolygonEntity p : polygons) {
            h = 31 * h + Objects.hash(p.getAreaId(), p.getMarkSessionId(), polygonHash(p.getPolygon()));
        }
        return h;
    }

    private static int poisHash(List<PoiCanonicalEntity> pois) {
        int h = pois.size();
        for (PoiCanonicalEntity p : pois) {
            h = 31 * h + Objects.hash(p.getCanonicalId(), p.getName(), p.getCategory(),
                    pointHash(p.getWorldPose()), pointHash(p.getDisplayPoint()), p.getRouteNodeId());
        }
        return h;
    }

    private static int stopsHash(List<VerticalConnectorStopEntity> stops) {
        int h = stops.size();
        for (VerticalConnectorStopEntity s : stops) {
            h = 31 * h + Objects.hash(s.getConnectorStopId(), s.getRouteNodeId(),
                    s.getArea() == null ? 0 : s.getArea().getAreaId());
        }
        return h;
    }

    private static int pointHash(Point p) {
        if (p == null) {
            return 0;
        }
        return Objects.hash(p.getX(), p.getY(), p.getCoordinate().getZ());
    }

    private static int polygonHash(Polygon poly) {
        if (poly == null) {
            return 0;
        }
        return poly.getCoordinates().length == 0 ? 1 : poly.toText().hashCode();
    }

    private static String originOf(java.util.Map<String, Object> sourceRef) {
        if (sourceRef == null) {
            return null;
        }
        Object v = sourceRef.get("origin");
        return v == null ? null : v.toString();
    }
}
