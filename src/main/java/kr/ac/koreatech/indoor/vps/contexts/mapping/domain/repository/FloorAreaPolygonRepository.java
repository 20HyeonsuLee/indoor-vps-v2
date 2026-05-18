package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaPolygonEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorAreaPolygonRepository extends JpaRepository<FloorAreaPolygonEntity, UUID> {

    List<FloorAreaPolygonEntity> findByScanId(UUID scanId);

    List<FloorAreaPolygonEntity> findByFloorArea_AreaId(UUID areaId);

    long deleteByScanId(UUID scanId);
}
