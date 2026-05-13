package kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.FloorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorRepository extends JpaRepository<FloorEntity, UUID> {
    List<FloorEntity> findByBuilding_BuildingIdOrderByLevelAscNameAsc(UUID buildingId);
}
