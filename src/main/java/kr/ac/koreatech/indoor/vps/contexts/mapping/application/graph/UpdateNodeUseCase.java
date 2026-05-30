package kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapNodeRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class UpdateNodeUseCase {

    private final MapNodeRepository nodeRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    public UpdateNodeUseCase(MapNodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    @Transactional
    public NodeResult update(UUID nodeId, UpdateNodeCommand command) {
        MapNodeEntity node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "NODE_NOT_FOUND", "node not found: " + nodeId));
        applyGeom(node, command);
        command.nodeType().ifPresent(node::changeNodeType);
        command.label().ifPresent(node::relabel);
        return NodeResult.from(nodeRepository.saveAndFlush(node));
    }

    private void applyGeom(MapNodeEntity node, UpdateNodeCommand command) {
        if (command.x().isEmpty() && command.y().isEmpty() && command.z().isEmpty()) {
            return;
        }
        Coordinate current = node.getGeom().getCoordinate();
        double newX = command.x().orElse(current.getX());
        double newY = command.y().orElse(current.getY());
        double newZ = command.z().orElse(current.getZ());
        Point newGeom = geometryFactory.createPoint(new Coordinate(newX, newY, newZ));
        node.relocate(newGeom);
    }
}
