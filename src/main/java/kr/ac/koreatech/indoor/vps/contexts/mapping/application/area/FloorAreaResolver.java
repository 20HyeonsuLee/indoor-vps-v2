package kr.ac.koreatech.indoor.vps.contexts.mapping.application.area;

import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorAreaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class FloorAreaResolver {

    private final FloorAreaRepository floorAreaRepository;

    public FloorAreaResolver(FloorAreaRepository floorAreaRepository) {
        this.floorAreaRepository = floorAreaRepository;
    }

    public FloorAreaEntity resolve(UUID floorId, Optional<UUID> areaId) {
        return areaId
                .map(id -> requireAreaBelongsToFloor(floorId, id))
                .orElseGet(() -> requireDefaultArea(floorId));
    }

    private FloorAreaEntity requireAreaBelongsToFloor(UUID floorId, UUID areaId) {
        FloorAreaEntity area = floorAreaRepository.findById(areaId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "AREA_NOT_FOUND", "area not found: " + areaId));
        if (!area.getFloor().getFloorId().equals(floorId)) {
            throw new ClientApiException(
                    HttpStatus.BAD_REQUEST, "AREA_FLOOR_MISMATCH",
                    "area " + areaId + " does not belong to floor " + floorId);
        }
        return area;
    }

    private FloorAreaEntity requireDefaultArea(UUID floorId) {
        return floorAreaRepository.findByFloor_FloorIdAndIsDefaultTrue(floorId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "DEFAULT_AREA_NOT_FOUND", "floor has no default area"));
    }
}
