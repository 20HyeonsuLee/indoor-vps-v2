package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingRepository extends JpaRepository<BuildingEntity, UUID> {
    List<BuildingEntity> findAllByOrderByCreatedAtAsc();

    List<BuildingEntity> findByStatusOrderByCreatedAtAsc(String status);
}
