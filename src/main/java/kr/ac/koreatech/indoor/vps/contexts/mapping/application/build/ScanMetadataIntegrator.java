package kr.ac.koreatech.indoor.vps.contexts.mapping.application.build;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.ArKitToRtabmap;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildingEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaPolygonEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.PoiCanonicalEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorStopEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.EdgeType;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.NodeType;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.Point3;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildingRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.VerticalConnectorRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.VerticalConnectorStopRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.BranchEdgeRow;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.BranchMarkRow;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.InterfloorMarkRow;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.PoiMarkRow;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.ScanMetadata;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.SessionInfo;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
class ScanMetadataIntegrator {

    private static final double SNAP_DISTANCE_M = 0.5;
    private static final String KIND_SEQUENTIAL = "sequential";

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final VerticalConnectorRepository verticalConnectorRepository;
    private final VerticalConnectorStopRepository verticalConnectorStopRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    ScanMetadataIntegrator(
            BuildingRepository buildingRepository,
            FloorRepository floorRepository,
            VerticalConnectorRepository verticalConnectorRepository,
            VerticalConnectorStopRepository verticalConnectorStopRepository
    ) {
        this.buildingRepository = buildingRepository;
        this.floorRepository = floorRepository;
        this.verticalConnectorRepository = verticalConnectorRepository;
        this.verticalConnectorStopRepository = verticalConnectorStopRepository;
    }

    IntegrationResult integrate(UUID scanId, UUID buildJobId, ScanMetadata metadata) {
        SessionInfo session = metadata.session();

        List<MapNodeEntity> nodes = new ArrayList<>();
        List<MapEdgeEntity> edges = new ArrayList<>();
        List<FloorAreaPolygonEntity> polygons = new ArrayList<>();
        List<PoiCanonicalEntity> pois = new ArrayList<>();
        List<VerticalConnectorStopEntity> stops = new ArrayList<>();

        Map<Long, MapNodeEntity> corridorById = buildCorridorNodes(
                scanId, buildJobId, metadata.branchMarks(), nodes);

        buildBranchEdges(scanId, buildJobId, metadata.branchEdges(), corridorById, edges);

        buildPolygons(scanId, buildJobId, metadata.branchMarks(), metadata.branchEdges(),
                session, polygons);

        applyPoiMarks(metadata.poiMarks(), session, scanId, buildJobId,
                corridorById, nodes, edges, pois);

        applyInterfloorMarks(metadata.interfloorMarks(), session, scanId, buildJobId,
                corridorById, nodes, edges, pois, stops);

        return new IntegrationResult(nodes, edges, polygons, pois, stops);
    }

    private Map<Long, MapNodeEntity> buildCorridorNodes(
            UUID scanId,
            UUID buildJobId,
            List<BranchMarkRow> marks,
            List<MapNodeEntity> nodes
    ) {
        Map<Long, MapNodeEntity> corridorById = new HashMap<>();
        for (BranchMarkRow mark : marks) {
            if (!"corridor".equals(mark.nodeType())) {
                continue;
            }
            Point3 rtPos = ArKitToRtabmap.convert(mark.tx(), mark.ty(), mark.tz());
            UUID nodeId = deterministicUuid("corridor-node:" + scanId + ":" + mark.id());
            Map<String, Object> ref = new HashMap<>();
            ref.put("branch_mark_id", mark.id());
            ref.put("connect_hint", mark.connectHint());
            MapNodeEntity node = MapNodeEntity.create(
                    nodeId, scanId, buildJobId, NodeType.corridor, point(rtPos), null);
            node.changeNodeType(NodeType.corridor, ref);
            nodes.add(node);
            corridorById.put(mark.id(), node);
        }
        return corridorById;
    }

    private void buildBranchEdges(
            UUID scanId,
            UUID buildJobId,
            List<BranchEdgeRow> branchEdges,
            Map<Long, MapNodeEntity> corridorById,
            List<MapEdgeEntity> edges
    ) {
        for (BranchEdgeRow edge : branchEdges) {
            if (!KIND_SEQUENTIAL.equals(edge.kind())) {
                continue;
            }
            MapNodeEntity from = corridorById.get(edge.fromMarkId());
            MapNodeEntity to = corridorById.get(edge.toMarkId());
            if (from == null || to == null) {
                continue;
            }
            Point3 fromPos = nodeCenter(from);
            Point3 toPos = nodeCenter(to);
            Coordinate fromCoord = new Coordinate(fromPos.x(), fromPos.y(), fromPos.z());
            Coordinate toCoord = new Coordinate(toPos.x(), toPos.y(), toPos.z());
            edges.add(MapEdgeEntity.create(
                    deterministicUuid("seq-edge:" + scanId + ":" + edge.id()),
                    scanId,
                    buildJobId,
                    from.getNodeId(),
                    to.getNodeId(),
                    EdgeType.rtabmap_link,
                    geometryFactory.createLineString(new Coordinate[]{fromCoord, toCoord}),
                    fromPos.distanceTo(toPos)
            ));
        }
    }

    private void buildPolygons(
            UUID scanId,
            UUID buildJobId,
            List<BranchMarkRow> allMarks,
            List<BranchEdgeRow> branchEdges,
            SessionInfo session,
            List<FloorAreaPolygonEntity> polygons
    ) {
        FloorEntity floor = resolveFloor(session).orElse(null);

        Map<String, List<BranchMarkRow>> cornersBySession = new HashMap<>();
        for (BranchMarkRow mark : allMarks) {
            if (!"corner".equals(mark.nodeType()) || mark.markSessionId() == null) {
                continue;
            }
            cornersBySession
                    .computeIfAbsent(String.valueOf(mark.markSessionId()), k -> new ArrayList<>())
                    .add(mark);
        }

        for (Map.Entry<String, List<BranchMarkRow>> entry : cornersBySession.entrySet()) {
            String sessionId = entry.getKey();
            List<BranchMarkRow> corners = entry.getValue();
            if (corners.size() < 3) {
                continue;
            }
            corners.sort(Comparator.comparingLong(BranchMarkRow::id));

            List<Long> cornerMarkIds = corners.stream().map(BranchMarkRow::id).toList();
            boolean hasClosed = branchEdges.stream()
                    .anyMatch(e -> "cornerPolygon".equals(e.kind())
                            && cornerMarkIds.contains(e.fromMarkId())
                            && cornerMarkIds.contains(e.toMarkId()));

            Coordinate[] coords = buildPolygonCoords(corners, hasClosed);
            if (coords == null) {
                continue;
            }
            LinearRing ring = geometryFactory.createLinearRing(coords);
            Polygon polygon = geometryFactory.createPolygon(ring);

            polygons.add(FloorAreaPolygonEntity.create(
                    deterministicUuid("polygon:" + scanId + ":" + sessionId),
                    scanId,
                    buildJobId,
                    floor,
                    sessionId,
                    polygon,
                    corners.stream().map(BranchMarkRow::id).toList()
            ));
        }
    }

    private Coordinate[] buildPolygonCoords(List<BranchMarkRow> corners, boolean closedHint) {
        List<Coordinate> coords = new ArrayList<>();
        for (BranchMarkRow corner : corners) {
            Point3 rt = ArKitToRtabmap.convert(corner.tx(), corner.ty(), corner.tz());
            coords.add(new Coordinate(rt.x(), rt.y(), rt.z()));
        }
        if (closedHint || coords.size() >= 3) {
            coords.add(coords.get(0));
        }
        if (coords.size() < 4) {
            return null;
        }
        return coords.toArray(new Coordinate[0]);
    }

    private void applyPoiMarks(
            List<PoiMarkRow> marks,
            SessionInfo session,
            UUID scanId,
            UUID buildJobId,
            Map<Long, MapNodeEntity> corridorById,
            List<MapNodeEntity> nodes,
            List<MapEdgeEntity> edges,
            List<PoiCanonicalEntity> pois
    ) {
        BuildingEntity building = resolveBuilding(session).orElse(null);
        FloorEntity floor = resolveFloor(session).orElse(null);

        for (PoiMarkRow mark : marks) {
            Point3 rtPos = ArKitToRtabmap.convert(mark.tx(), mark.ty(), mark.tz());
            UUID nodeId = deterministicUuid("poi-node:" + scanId + ":" + mark.id());
            Point geom = point(rtPos);

            MapNodeEntity poiNode = MapNodeEntity.create(
                    nodeId, scanId, buildJobId, NodeType.poi, geom, mark.label());
            nodes.add(poiNode);

            PoiCanonicalEntity canonical = PoiCanonicalEntity.createFromMark(
                    deterministicUuid("poi-canonical:" + scanId + ":" + mark.id()),
                    scanId,
                    building,
                    floor,
                    session.floorLevel(),
                    mark.label(),
                    "unknown",
                    geom,
                    nodeId,
                    List.of(mark.id())
            );
            pois.add(canonical);

            findNearestCorridor(rtPos, corridorById).ifPresent(nearest -> {
                edges.add(spurEdge(scanId, buildJobId, nodeId, nearest.getNodeId(),
                        rtPos, nodeCenter(nearest)));
            });
        }
    }

    private void applyInterfloorMarks(
            List<InterfloorMarkRow> marks,
            SessionInfo session,
            UUID scanId,
            UUID buildJobId,
            Map<Long, MapNodeEntity> corridorById,
            List<MapNodeEntity> nodes,
            List<MapEdgeEntity> edges,
            List<PoiCanonicalEntity> pois,
            List<VerticalConnectorStopEntity> stops
    ) {
        BuildingEntity building = resolveBuilding(session).orElse(null);
        if (building == null) {
            return;
        }

        for (InterfloorMarkRow mark : marks) {
            Point3 rtPos = ArKitToRtabmap.convert(mark.tx(), mark.ty(), mark.tz());
            UUID nodeId = deterministicUuid("interfloor-node:" + scanId + ":" + mark.id());
            Point geom = point(rtPos);
            String connectorKey = mark.prefix() != null ? mark.prefix() : String.valueOf(mark.id());
            String levelId = session.floorLevel() != null ? session.floorLevel() : "level-0";

            MapNodeEntity connectorNode = MapNodeEntity.create(
                    nodeId, scanId, buildJobId, NodeType.poi, geom, connectorKey);
            nodes.add(connectorNode);

            VerticalConnectorEntity connector = upsertConnector(building, mark.connectorType(), connectorKey);
            PoiCanonicalEntity stopPoi = PoiCanonicalEntity.createFromMark(
                    deterministicUuid("interfloor-poi:" + scanId + ":" + mark.id()),
                    scanId,
                    building,
                    resolveFloor(session).orElse(null),
                    levelId,
                    connectorKey,
                    mark.connectorType() != null ? mark.connectorType() : "unknown",
                    geom,
                    nodeId,
                    List.of(mark.id())
            );
            pois.add(stopPoi);
            buildStop(connector, levelId, stopPoi, nodeId).ifPresent(stops::add);

            findNearestCorridor(rtPos, corridorById).ifPresent(nearest -> {
                edges.add(spurEdge(scanId, buildJobId, nodeId, nearest.getNodeId(),
                        rtPos, nodeCenter(nearest)));
            });
        }
    }

    private Optional<MapNodeEntity> findNearestCorridor(
            Point3 target,
            Map<Long, MapNodeEntity> corridorById
    ) {
        MapNodeEntity best = null;
        double bestDist = SNAP_DISTANCE_M;
        for (MapNodeEntity node : corridorById.values()) {
            double d = target.distanceTo(nodeCenter(node));
            if (d < bestDist) {
                bestDist = d;
                best = node;
            }
        }
        return Optional.ofNullable(best);
    }

    private VerticalConnectorEntity upsertConnector(
            BuildingEntity building,
            String connectorType,
            String connectorKey
    ) {
        String type = connectorType != null ? connectorType : "unknown";
        return verticalConnectorRepository
                .findByBuilding_BuildingIdAndConnectorTypeAndConnectorKey(
                        building.getBuildingId(), type, connectorKey)
                .orElseGet(() -> {
                    VerticalConnectorEntity entity = VerticalConnectorEntity.create(
                            deterministicUuid("connector:" + building.getBuildingId()
                                    + ":" + type + ":" + connectorKey),
                            building,
                            type,
                            connectorKey,
                            connectorKey
                    );
                    return verticalConnectorRepository.save(entity);
                });
    }

    private Optional<VerticalConnectorStopEntity> buildStop(
            VerticalConnectorEntity connector,
            String levelId,
            PoiCanonicalEntity poi,
            UUID routeNodeId
    ) {
        boolean exists = verticalConnectorStopRepository
                .findByConnector_ConnectorIdAndLevelId(connector.getConnectorId(), levelId)
                .isPresent();
        if (exists) {
            return Optional.empty();
        }
        return Optional.of(VerticalConnectorStopEntity.create(
                UUID.randomUUID(), connector, levelId, poi, routeNodeId));
    }

    private MapEdgeEntity spurEdge(
            UUID scanId,
            UUID buildJobId,
            UUID fromId,
            UUID toId,
            Point3 fromPos,
            Point3 toPos
    ) {
        Coordinate fromCoord = new Coordinate(fromPos.x(), fromPos.y(), fromPos.z());
        Coordinate toCoord = new Coordinate(toPos.x(), toPos.y(), toPos.z());
        return MapEdgeEntity.create(
                deterministicUuid("spur:" + scanId + ":" + fromId + ":" + toId),
                scanId,
                buildJobId,
                fromId,
                toId,
                EdgeType.poi_spur,
                geometryFactory.createLineString(new Coordinate[]{fromCoord, toCoord}),
                fromPos.distanceTo(toPos)
        );
    }

    private Point point(Point3 p) {
        return geometryFactory.createPoint(new Coordinate(p.x(), p.y(), p.z()));
    }

    private Point3 nodeCenter(MapNodeEntity node) {
        return new Point3(
                node.getGeom().getX(),
                node.getGeom().getY(),
                node.getGeom().getCoordinate().getZ()
        );
    }

    private Optional<BuildingEntity> resolveBuilding(SessionInfo session) {
        if (session.buildingId() == null) {
            return Optional.empty();
        }
        try {
            return buildingRepository.findById(UUID.fromString(session.buildingId()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<FloorEntity> resolveFloor(SessionInfo session) {
        if (session.floorId() == null) {
            return Optional.empty();
        }
        try {
            return floorRepository.findById(UUID.fromString(session.floorId()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private UUID deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    record IntegrationResult(
            List<MapNodeEntity> nodes,
            List<MapEdgeEntity> edges,
            List<FloorAreaPolygonEntity> polygons,
            List<PoiCanonicalEntity> pois,
            List<VerticalConnectorStopEntity> stops
    ) {
    }
}
