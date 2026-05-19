package kr.ac.koreatech.indoor.vps.contexts.mapping.application.polygon;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaPolygonEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorAreaPolygonRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class UpdatePolygonUseCase {

    private final FloorAreaPolygonRepository polygonRepository;
    private final PolygonGeometryFactory polygonFactory;

    public UpdatePolygonUseCase(FloorAreaPolygonRepository polygonRepository, PolygonGeometryFactory polygonFactory) {
        this.polygonRepository = polygonRepository;
        this.polygonFactory = polygonFactory;
    }

    @Transactional
    public PolygonResult update(UUID polygonId, PolygonCommand command) {
        FloorAreaPolygonEntity entity = polygonRepository.findById(polygonId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "POLYGON_NOT_FOUND", "polygon not found: " + polygonId));
        entity.replacePolygon(polygonFactory.polygon(command.exterior()));
        return PolygonResult.from(polygonRepository.saveAndFlush(entity));
    }
}
