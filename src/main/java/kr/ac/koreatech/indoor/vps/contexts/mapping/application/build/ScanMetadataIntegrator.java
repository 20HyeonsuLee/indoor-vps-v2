package kr.ac.koreatech.indoor.vps.contexts.mapping.application.build;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.ArKitToRtabmap;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildingEntity;
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
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.PoiCanonicalRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.VerticalConnectorRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.VerticalConnectorStopRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.BranchMarkRow;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.InterfloorMarkRow;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.KeyframeRow;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.PoiMarkRow;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.ScanMetadata;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.SessionInfo;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
class ScanMetadataIntegrator {

    private static final double SNAP_DISTANCE_M = 0.5;

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final PoiCanonicalRepository poiCanonicalRepository;
    private final VerticalConnectorRepository verticalConnectorRepository;
    private final VerticalConnectorStopRepository verticalConnectorStopRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    ScanMetadataIntegrator(
            BuildingRepository buildingRepository,
            FloorRepository floorRepository,
            PoiCanonicalRepository poiCanonicalRepository,
            VerticalConnectorRepository verticalConnectorRepository,
            VerticalConnectorStopRepository verticalConnectorStopRepository
    ) {
        this.buildingRepository = buildingRepository;
        this.floorRepository = floorRepository;
        this.poiCanonicalRepository = poiCanonicalRepository;
        this.verticalConnectorRepository = verticalConnectorRepository;
        this.verticalConnectorStopRepository = verticalConnectorStopRepository;
    }

    IntegrationResult integrate(
            UUID scanId,
            UUID buildJobId,
            ScanMetadata metadata,
            List<MapNodeEntity> rtabmapNodes
    ) {
        NodeIndex nodeIndex = NodeIndex.build(rtabmapNodes, metadata.keyframes());
        SessionInfo session = metadata.session();

        List<MapNodeEntity> extraNodes = new ArrayList<>();
        List<MapEdgeEntity> extraEdges = new ArrayList<>();

        applyBranchMarks(metadata.branchMarks(), nodeIndex);
        applyPoiMarks(metadata.poiMarks(), session, scanId, buildJobId, nodeIndex, extraNodes, extraEdges);
        applyInterfloorMarks(metadata.interfloorMarks(), session, scanId, buildJobId, nodeIndex, extraNodes, extraEdges);

        return new IntegrationResult(extraNodes, extraEdges);
    }

    private void applyBranchMarks(List<BranchMarkRow> marks, NodeIndex index) {
        for (BranchMarkRow mark : marks) {
            Point3 rtPos = ArKitToRtabmap.convert(mark.tx(), mark.ty(), mark.tz());
            MapNodeEntity nearest = index.findNearest(rtPos, SNAP_DISTANCE_M).orElse(null);
            if (nearest == null) {
                continue;
            }
            NodeType newType = "corner".equals(mark.nodeType()) ? NodeType.junction : NodeType.corridor;
            Map<String, Object> ref = new HashMap<>();
            ref.put("branch_mark_id", mark.id());
            ref.put("connect_hint", mark.connectHint());
            nearest.changeNodeType(newType, ref);
        }
    }

    private void applyPoiMarks(
            List<PoiMarkRow> marks,
            SessionInfo session,
            UUID scanId,
            UUID buildJobId,
            NodeIndex index,
            List<MapNodeEntity> extraNodes,
            List<MapEdgeEntity> extraEdges
    ) {
        BuildingEntity building = resolveBuilding(session).orElse(null);
        FloorEntity floor = resolveFloor(session).orElse(null);

        for (PoiMarkRow mark : marks) {
            Point3 rtPos = ArKitToRtabmap.convert(mark.tx(), mark.ty(), mark.tz());
            UUID nodeId = deterministicUuid("poi-node:" + scanId + ":" + mark.id());
            Point geom = point(rtPos);

            MapNodeEntity poiNode = MapNodeEntity.create(nodeId, scanId, buildJobId, NodeType.poi, geom, mark.label());
            extraNodes.add(poiNode);

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
            poiCanonicalRepository.save(canonical);

            index.findNearest(rtPos, SNAP_DISTANCE_M).ifPresent(nearest -> {
                MapEdgeEntity spur = spurEdge(scanId, buildJobId, nodeId, nearest.getNodeId(), rtPos, nodeCenter(nearest));
                extraEdges.add(spur);
            });
        }
    }

    private void applyInterfloorMarks(
            List<InterfloorMarkRow> marks,
            SessionInfo session,
            UUID scanId,
            UUID buildJobId,
            NodeIndex index,
            List<MapNodeEntity> extraNodes,
            List<MapEdgeEntity> extraEdges
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

            MapNodeEntity connectorNode = MapNodeEntity.create(nodeId, scanId, buildJobId, NodeType.poi, geom, connectorKey);
            extraNodes.add(connectorNode);

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
            poiCanonicalRepository.save(stopPoi);

            upsertStop(connector, levelId, stopPoi, nodeId);

            index.findNearest(rtPos, SNAP_DISTANCE_M).ifPresent(nearest -> {
                MapEdgeEntity spur = spurEdge(scanId, buildJobId, nodeId, nearest.getNodeId(), rtPos, nodeCenter(nearest));
                extraEdges.add(spur);
            });
        }
    }

    private VerticalConnectorEntity upsertConnector(BuildingEntity building, String connectorType, String connectorKey) {
        String type = connectorType != null ? connectorType : "unknown";
        return verticalConnectorRepository
                .findByBuilding_BuildingIdAndConnectorTypeAndConnectorKey(building.getBuildingId(), type, connectorKey)
                .orElseGet(() -> {
                    VerticalConnectorEntity entity = VerticalConnectorEntity.create(
                            deterministicUuid("connector:" + building.getBuildingId() + ":" + type + ":" + connectorKey),
                            building,
                            type,
                            connectorKey,
                            connectorKey
                    );
                    return verticalConnectorRepository.save(entity);
                });
    }

    private void upsertStop(VerticalConnectorEntity connector, String levelId, PoiCanonicalEntity poi, UUID routeNodeId) {
        verticalConnectorStopRepository
                .findByConnector_ConnectorIdAndLevelId(connector.getConnectorId(), levelId)
                .ifPresentOrElse(
                        existing -> { /* already exists — keep */ },
                        () -> verticalConnectorStopRepository.save(
                                VerticalConnectorStopEntity.create(
                                        UUID.randomUUID(),
                                        connector,
                                        levelId,
                                        poi,
                                        routeNodeId
                                )
                        )
                );
    }

    private MapEdgeEntity spurEdge(UUID scanId, UUID buildJobId, UUID fromId, UUID toId, Point3 fromPos, Point3 toPos) {
        Coordinate fromCoord = new Coordinate(fromPos.x(), fromPos.y(), fromPos.z());
        Coordinate toCoord = new Coordinate(toPos.x(), toPos.y(), toPos.z());
        double length = fromPos.distanceTo(toPos);
        return MapEdgeEntity.create(
                deterministicUuid("spur:" + scanId + ":" + fromId + ":" + toId),
                scanId,
                buildJobId,
                fromId,
                toId,
                EdgeType.poi_spur,
                geometryFactory.createLineString(new Coordinate[]{fromCoord, toCoord}),
                length
        );
    }

    private Point point(Point3 p) {
        return geometryFactory.createPoint(new Coordinate(p.x(), p.y(), p.z()));
    }

    private Point3 nodeCenter(MapNodeEntity node) {
        return new Point3(node.getGeom().getX(), node.getGeom().getY(), node.getGeom().getCoordinate().getZ());
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

    record IntegrationResult(List<MapNodeEntity> extraNodes, List<MapEdgeEntity> extraEdges) {
    }

    /** rtabmap node 목록을 공간 인덱스로 감싼다. keyframe seq → rtabmap node 매핑도 포함. */
    private static final class NodeIndex {

        private final List<MapNodeEntity> nodes;
        private final Map<Integer, MapNodeEntity> byRtabmapId;

        private NodeIndex(List<MapNodeEntity> nodes, Map<Integer, MapNodeEntity> byRtabmapId) {
            this.nodes = nodes;
            this.byRtabmapId = byRtabmapId;
        }

        static NodeIndex build(List<MapNodeEntity> nodes, List<KeyframeRow> keyframes) {
            // Branch-mark nearest-node lookup uses spatial distance only (pose 거리).
            // keyframe → rtabmap_node_id 매핑은 향후 정밀 스냅에 활용 가능하나
            // 현재는 byRtabmapId 를 빈 맵으로 두고 findNearest 에서 거리 기반으로만 동작한다.
            return new NodeIndex(nodes, new HashMap<>());
        }

        Optional<MapNodeEntity> findNearest(Point3 target, double maxDist) {
            MapNodeEntity best = null;
            double bestDist = maxDist;
            for (MapNodeEntity node : nodes) {
                Point3 nodePos = new Point3(
                        node.getGeom().getX(),
                        node.getGeom().getY(),
                        node.getGeom().getCoordinate().getZ()
                );
                double d = target.distanceTo(nodePos);
                if (d < bestDist) {
                    bestDist = d;
                    best = node;
                }
            }
            return Optional.ofNullable(best);
        }
    }
}
