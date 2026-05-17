package kr.ac.koreatech.indoor.vps.contexts.mapping.application.area;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorAreaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class FloorAreaUseCase {

    private final FloorQueryService floorQueryService;
    private final FloorAreaRepository floorAreaRepository;

    public FloorAreaUseCase(FloorQueryService floorQueryService, FloorAreaRepository floorAreaRepository) {
        this.floorQueryService = floorQueryService;
        this.floorAreaRepository = floorAreaRepository;
    }

    public List<AreaResult> listAreas(UUID floorId) {
        floorQueryService.requireFloor(floorId);
        return floorAreaRepository.findByFloor_FloorIdOrderByAreaIndexAsc(floorId).stream()
                .map(this::toResult)
                .toList();
    }

    @Transactional
    public AreaResult createArea(UUID floorId, CreateAreaCommand command) {
        FloorEntity floor = floorQueryService.requireFloor(floorId);
        int nextIndex = floorAreaRepository.countByFloor_FloorId(floorId);
        FloorAreaEntity area = FloorAreaEntity.create(floor, nextIndex, command.label());
        return toResult(floorAreaRepository.saveAndFlush(area));
    }

    public FloorAreaEntity requireDefaultArea(UUID floorId) {
        return floorAreaRepository.findByFloor_FloorIdAndIsDefaultTrue(floorId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "DEFAULT_AREA_NOT_FOUND", "floor has no default area"));
    }

    private AreaResult toResult(FloorAreaEntity area) {
        return new AreaResult(
                area.getAreaId(),
                area.getFloor().getFloorId(),
                area.getAreaIndex(),
                area.getLabel(),
                area.isDefault(),
                area.getCreatedAt()
        );
    }
}
