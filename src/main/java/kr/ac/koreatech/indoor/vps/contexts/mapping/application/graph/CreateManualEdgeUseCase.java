package kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.EdgeType;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapEdgeRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapNodeRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class CreateManualEdgeUseCase {

    private final MapNodeRepository nodeRepository;
    private final MapEdgeRepository edgeRepository;
    private final ManualEditScopeResolver scopeResolver;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    public CreateManualEdgeUseCase(
            MapNodeRepository nodeRepository,
            MapEdgeRepository edgeRepository,
            ManualEditScopeResolver scopeResolver
    ) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.scopeResolver = scopeResolver;
    }

    @Transactional
    public EdgeResult create(UUID areaId, CreateEdgeCommand command) {
        ManualEditScope scope = scopeResolver.resolve(areaId);
        MapNodeEntity from = requireNode(command.fromNodeId());
        MapNodeEntity to = requireNode(command.toNodeId());
        Coordinate fromCoord = from.getGeom().getCoordinate();
        Coordinate toCoord = to.getGeom().getCoordinate();
        LineString geom = geometryFactory.createLineString(new Coordinate[]{fromCoord, toCoord});
        double lengthM = computeLength(fromCoord, toCoord);
        MapEdgeEntity edge = MapEdgeEntity.create(
                UUID.randomUUID(),
                scope.scanId(),
                scope.buildJobId(),
                scope.area().getAreaId(),
                command.fromNodeId(),
                command.toNodeId(),
                command.edgeType().orElse(EdgeType.rtabmap_link),
                geom,
                lengthM
        );
        return EdgeResult.from(edgeRepository.saveAndFlush(edge));
    }

    private MapNodeEntity requireNode(UUID nodeId) {
        return nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "NODE_NOT_FOUND", "node not found: " + nodeId));
    }

    private double computeLength(Coordinate a, Coordinate b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
