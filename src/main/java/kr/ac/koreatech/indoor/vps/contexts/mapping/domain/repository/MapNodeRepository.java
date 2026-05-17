package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapNodeRepository extends JpaRepository<MapNodeEntity, UUID> {
    List<MapNodeEntity> findByScanIdAndStaleFalseOrderByNodeId(UUID scanId);

    boolean existsByScanIdAndStaleFalse(UUID scanId);

    long deleteByScanId(UUID scanId);
}
