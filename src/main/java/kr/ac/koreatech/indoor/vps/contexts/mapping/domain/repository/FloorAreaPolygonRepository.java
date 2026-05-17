package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaPolygonEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorAreaPolygonRepository extends JpaRepository<FloorAreaPolygonEntity, UUID> {

    long deleteByScanId(UUID scanId);
}
