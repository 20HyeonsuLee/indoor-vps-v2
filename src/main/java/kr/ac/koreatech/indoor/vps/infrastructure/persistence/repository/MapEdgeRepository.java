package kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.MapEdgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapEdgeRepository extends JpaRepository<MapEdgeEntity, UUID> {
    List<MapEdgeEntity> findByScanIdAndStaleFalseOrderByEdgeId(UUID scanId);

    long deleteByScanId(UUID scanId);
}
