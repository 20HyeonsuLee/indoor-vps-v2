package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapEdgeRepository extends JpaRepository<MapEdgeEntity, UUID> {
    List<MapEdgeEntity> findByScanIdAndStaleFalseOrderByEdgeId(UUID scanId);

    long deleteByScanId(UUID scanId);
}
