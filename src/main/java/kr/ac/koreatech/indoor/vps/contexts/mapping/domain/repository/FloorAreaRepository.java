package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorAreaRepository extends JpaRepository<FloorAreaEntity, UUID> {

    Optional<FloorAreaEntity> findByFloor_FloorIdAndIsDefaultTrue(UUID floorId);

    List<FloorAreaEntity> findByFloor_FloorIdOrderByAreaIndexAsc(UUID floorId);

    int countByFloor_FloorId(UUID floorId);
}
