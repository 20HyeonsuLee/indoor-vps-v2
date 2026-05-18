package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.MapDtos.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.NavigationGeometry;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaPolygonEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.PoiCanonicalEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorStopEntity;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.springframework.stereotype.Component;

@Component
public class FloorMapResponseMapper {

    public FloorMapNode floorMapNode(
            MapNodeEntity node,
            Map<UUID, PoiCanonicalEntity> poiByRouteNodeId,
            Map<UUID, VerticalConnectorStopEntity> stopByRouteNodeId
    ) {
        FloorMapConnector connector = null;
        String category = null;
        VerticalConnectorStopEntity stop = stopByRouteNodeId.get(node.getNodeId());
        if (stop != null) {
            connector = new FloorMapConnector(
                    stop.getConnector().getConnectorType(),
                    stop.getConnector().getConnectorKey()
            );
            category = stop.getConnector().getConnectorType();
        } else {
            PoiCanonicalEntity poi = poiByRouteNodeId.get(node.getNodeId());
            if (poi != null) {
                category = poi.getCategory();
            }
        }
        return new FloorMapNode(
                node.getNodeId(),
                node.getNodeType().name(),
                NavigationGeometry.x(node.getGeom()),
                NavigationGeometry.y(node.getGeom()),
                NavigationGeometry.z(node.getGeom()),
                node.getLabel(),
                category,
                connector
        );
    }

    public List<FloorMapDestination> destinations(
            List<PoiCanonicalEntity> pois,
            Map<UUID, VerticalConnectorStopEntity> stopByRouteNodeId
    ) {
        List<FloorMapDestination> out = new ArrayList<>();
        for (PoiCanonicalEntity p : pois) {
            if (p.getRouteNodeId() != null && stopByRouteNodeId.containsKey(p.getRouteNodeId())) {
                // 층간연결 stop POI는 connectors[]로 분리되므로 destinations에서 제외.
                continue;
            }
            Point point = p.getDisplayPoint() != null ? p.getDisplayPoint() : p.getWorldPose();
            out.add(new FloorMapDestination(
                    p.getCanonicalId(),
                    p.getRouteNodeId(),
                    p.getName(),
                    p.getLabel(),
                    p.getCategory(),
                    point == null ? 0.0 : NavigationGeometry.x(point),
                    point == null ? 0.0 : NavigationGeometry.y(point),
                    point == null ? 0.0 : NavigationGeometry.z(point)
            ));
        }
        return out;
    }

    public List<FloorMapConnectorRef> connectors(
            List<VerticalConnectorStopEntity> stopsInArea,
            List<VerticalConnectorStopEntity> stopsInBuilding,
            Map<UUID, MapNodeEntity> nodeById
    ) {
        Map<UUID, List<VerticalConnectorStopEntity>> stopsByConnector = new HashMap<>();
        for (VerticalConnectorStopEntity s : stopsInBuilding) {
            stopsByConnector
                    .computeIfAbsent(s.getConnector().getConnectorId(), k -> new ArrayList<>())
                    .add(s);
        }

        List<FloorMapConnectorRef> out = new ArrayList<>();
        for (VerticalConnectorStopEntity localStop : stopsInArea) {
            UUID connectorId = localStop.getConnector().getConnectorId();
            List<VerticalConnectorStopEntity> siblings = stopsByConnector.getOrDefault(connectorId, List.of());
            List<FloorMapConnectorStop> stopsOut = new ArrayList<>();
            for (VerticalConnectorStopEntity sib : siblings) {
                MapNodeEntity refNode = sib.getRouteNodeId() == null ? null : nodeById.get(sib.getRouteNodeId());
                Double sx = null, sy = null, sz = null;
                if (refNode != null) {
                    sx = NavigationGeometry.x(refNode.getGeom());
                    sy = NavigationGeometry.y(refNode.getGeom());
                    sz = NavigationGeometry.z(refNode.getGeom());
                }
                stopsOut.add(new FloorMapConnectorStop(
                        sib.getArea().getFloor().getFloorId(),
                        sib.getArea().getFloor().getLevel(),
                        sib.getArea().getAreaId(),
                        sib.getArea().getLabel(),
                        sib.getRouteNodeId(),
                        sx, sy, sz
                ));
            }
            MapNodeEntity localNode = localStop.getRouteNodeId() == null ? null : nodeById.get(localStop.getRouteNodeId());
            Double lx = null, ly = null, lz = null;
            if (localNode != null) {
                lx = NavigationGeometry.x(localNode.getGeom());
                ly = NavigationGeometry.y(localNode.getGeom());
                lz = NavigationGeometry.z(localNode.getGeom());
            }
            out.add(new FloorMapConnectorRef(
                    connectorId,
                    localStop.getConnector().getConnectorType(),
                    localStop.getConnector().getConnectorKey(),
                    localStop.getConnector().getName(),
                    localStop.getRouteNodeId(),
                    lx, ly, lz,
                    stopsOut
            ));
        }
        return out;
    }

    public FloorMapEdge floorMapEdge(MapEdgeEntity edge) {
        return new FloorMapEdge(
                edge.getEdgeId(),
                edge.getFromNodeId(),
                edge.getToNodeId(),
                edge.getLengthM(),
                edge.getEdgeType().name()
        );
    }

    public Map<String, Object> polygonFeatureCollection(List<FloorAreaPolygonEntity> polygons) {
        List<Polygon> input = new ArrayList<>();
        List<UUID> sourceIds = new ArrayList<>();
        List<String> sourceSessions = new ArrayList<>();
        for (FloorAreaPolygonEntity p : polygons) {
            Polygon poly = p.getPolygon();
            if (poly == null || poly.isEmpty()) {
                continue;
            }
            input.add(poly);
            sourceIds.add(p.getAreaId());
            if (p.getMarkSessionId() != null) {
                sourceSessions.add(p.getMarkSessionId());
            }
        }
        if (input.isEmpty()) {
            return Map.of("type", "FeatureCollection", "features", List.of());
        }
        Geometry union = UnaryUnionOp.union(input);
        Map<String, Object> geometry = geometryToGeoJson(union);
        if (geometry == null) {
            return Map.of("type", "FeatureCollection", "features", List.of());
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("sourcePolygonIds", sourceIds);
        properties.put("sourceSessions", sourceSessions);
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("geometry", geometry);
        feature.put("properties", properties);
        return Map.of("type", "FeatureCollection", "features", List.of(feature));
    }

    private Map<String, Object> geometryToGeoJson(Geometry g) {
        if (g == null || g.isEmpty()) {
            return null;
        }
        if (g instanceof Polygon poly) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "Polygon");
            result.put("coordinates", polygonRings(poly));
            return result;
        }
        if (g instanceof MultiPolygon mp) {
            List<List<List<List<Double>>>> coords = new ArrayList<>();
            for (int i = 0; i < mp.getNumGeometries(); i++) {
                coords.add(polygonRings((Polygon) mp.getGeometryN(i)));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "MultiPolygon");
            result.put("coordinates", coords);
            return result;
        }
        // UnaryUnionOp can return GeometryCollection when mixed types appear; extract polygons.
        List<Polygon> extracted = new ArrayList<>();
        for (int i = 0; i < g.getNumGeometries(); i++) {
            Geometry sub = g.getGeometryN(i);
            if (sub instanceof Polygon p) {
                extracted.add(p);
            }
        }
        if (extracted.isEmpty()) {
            return null;
        }
        if (extracted.size() == 1) {
            return geometryToGeoJson(extracted.get(0));
        }
        GeometryFactory gf = g.getFactory();
        return geometryToGeoJson(gf.createMultiPolygon(extracted.toArray(new Polygon[0])));
    }

    private List<List<List<Double>>> polygonRings(Polygon polygon) {
        List<List<List<Double>>> rings = new ArrayList<>();
        rings.add(ringCoords(polygon.getExteriorRing()));
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            rings.add(ringCoords(polygon.getInteriorRingN(i)));
        }
        return rings;
    }

    private List<List<Double>> ringCoords(LineString ring) {
        List<List<Double>> out = new ArrayList<>();
        for (Coordinate c : ring.getCoordinates()) {
            out.add(List.of(c.x, c.y));
        }
        return out;
    }

    public FloorMapBounds floorMapBounds(List<MapNodeEntity> nodes) {
        if (nodes.isEmpty()) {
            return new FloorMapBounds(0, 0, 0, 0, 0, 0);
        }
        Bounds bounds = computeBounds(nodes);
        return new FloorMapBounds(
                bounds.minX(),
                bounds.minY(),
                bounds.maxX(),
                bounds.maxY(),
                bounds.maxX() - bounds.minX(),
                bounds.maxY() - bounds.minY()
        );
    }

    public Map<String, Object> nodeMap(MapNodeEntity node) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", node.getNodeId());
        result.put("type", node.getNodeType().name());
        result.put("x", NavigationGeometry.x(node.getGeom()));
        result.put("y", NavigationGeometry.y(node.getGeom()));
        result.put("z", NavigationGeometry.z(node.getGeom()));
        result.put("label", node.getLabel());
        return result;
    }

    public Map<String, Object> edgeMap(MapEdgeEntity edge) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", edge.getEdgeId());
        result.put("fromNodeId", edge.getFromNodeId());
        result.put("toNodeId", edge.getToNodeId());
        result.put("lengthM", edge.getLengthM());
        result.put("type", edge.getEdgeType().name());
        return result;
    }

    private Bounds computeBounds(List<MapNodeEntity> nodes) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (MapNodeEntity node : nodes) {
            minX = Math.min(minX, NavigationGeometry.x(node.getGeom()));
            minY = Math.min(minY, NavigationGeometry.y(node.getGeom()));
            maxX = Math.max(maxX, NavigationGeometry.x(node.getGeom()));
            maxY = Math.max(maxY, NavigationGeometry.y(node.getGeom()));
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    private record Bounds(double minX, double minY, double maxX, double maxY) {
    }
}
