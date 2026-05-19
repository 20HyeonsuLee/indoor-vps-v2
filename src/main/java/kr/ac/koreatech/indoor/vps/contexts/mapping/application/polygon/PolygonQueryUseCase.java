package kr.ac.koreatech.indoor.vps.contexts.mapping.application.polygon;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorAreaPolygonRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorAreaRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class PolygonQueryUseCase {

    private final FloorAreaPolygonRepository polygonRepository;
    private final FloorAreaRepository areaRepository;

    public PolygonQueryUseCase(
            FloorAreaPolygonRepository polygonRepository,
            FloorAreaRepository areaRepository
    ) {
        this.polygonRepository = polygonRepository;
        this.areaRepository = areaRepository;
    }

    public List<PolygonResult> listByArea(UUID areaId) {
        if (!areaRepository.existsById(areaId)) {
            throw new ClientApiException(
                    HttpStatus.NOT_FOUND, "AREA_NOT_FOUND", "area not found: " + areaId);
        }
        return polygonRepository.findByFloorArea_AreaIdOrderByAreaId(areaId).stream()
                .map(PolygonResult::from)
                .toList();
    }
}
