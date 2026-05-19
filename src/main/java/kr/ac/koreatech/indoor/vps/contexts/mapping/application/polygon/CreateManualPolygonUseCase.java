package kr.ac.koreatech.indoor.vps.contexts.mapping.application.polygon;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.ManualEditScope;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.ManualEditScopeResolver;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaPolygonEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorAreaPolygonRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class CreateManualPolygonUseCase {

    private final FloorAreaPolygonRepository polygonRepository;
    private final ManualEditScopeResolver scopeResolver;
    private final PolygonGeometryFactory polygonFactory;

    public CreateManualPolygonUseCase(
            FloorAreaPolygonRepository polygonRepository,
            ManualEditScopeResolver scopeResolver,
            PolygonGeometryFactory polygonFactory
    ) {
        this.polygonRepository = polygonRepository;
        this.scopeResolver = scopeResolver;
        this.polygonFactory = polygonFactory;
    }

    @Transactional
    public PolygonResult create(UUID areaId, PolygonCommand command) {
        ManualEditScope scope = scopeResolver.resolve(areaId);
        FloorAreaPolygonEntity entity = FloorAreaPolygonEntity.createManual(
                UUID.randomUUID(),
                scope.scanId(),
                scope.buildJobId(),
                scope.area().getFloor(),
                scope.area(),
                "manual_edit",
                polygonFactory.polygon(command.exterior())
        );
        return PolygonResult.from(polygonRepository.saveAndFlush(entity));
    }
}
