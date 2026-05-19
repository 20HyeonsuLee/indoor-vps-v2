package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapNodeRepository extends JpaRepository<MapNodeEntity, UUID> {
    List<MapNodeEntity> findByScanIdAndStaleFalseOrderByNodeId(UUID scanId);

    boolean existsByScanIdAndStaleFalse(UUID scanId);

    long deleteByScanId(UUID scanId);

    List<MapNodeEntity> findByAreaIdAndStaleFalseOrderByNodeId(UUID areaId);

    @Modifying
    @Query(value = """
            delete from map_node
            where area_id = :areaId
              and source_ref ->> 'origin' = 'manual_edit'
            """, nativeQuery = true)
    int deleteManualByAreaId(@Param("areaId") UUID areaId);
}
