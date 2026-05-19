package kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapNodeRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class CreateManualNodeUseCase {

    private final MapNodeRepository nodeRepository;
    private final ManualEditScopeResolver scopeResolver;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    public CreateManualNodeUseCase(
            MapNodeRepository nodeRepository,
            ManualEditScopeResolver scopeResolver
    ) {
        this.nodeRepository = nodeRepository;
        this.scopeResolver = scopeResolver;
    }

    @Transactional
    public NodeResult create(UUID areaId, CreateNodeCommand command) {
        ManualEditScope scope = scopeResolver.resolve(areaId);
        Point geom = geometryFactory.createPoint(new Coordinate(command.x(), command.y(), command.z()));
        MapNodeEntity node = MapNodeEntity.create(
                UUID.randomUUID(),
                scope.scanId(),
                scope.buildJobId(),
                scope.area().getAreaId(),
                command.nodeType(),
                geom,
                command.label()
        );
        node.markManual();
        MapNodeEntity saved = nodeRepository.saveAndFlush(node);
        return NodeResult.from(saved);
    }
}
