package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaPolygonEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FloorAreaPolygonRepository extends JpaRepository<FloorAreaPolygonEntity, UUID> {

    long deleteByScanId(UUID scanId);

    List<FloorAreaPolygonEntity> findByScanIdOrderByAreaId(UUID scanId);

    List<FloorAreaPolygonEntity> findByFloorArea_AreaIdOrderByAreaId(UUID floorAreaId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from FloorAreaPolygonEntity p
            where p.floorArea.areaId = :areaId
              and p.markSessionId = 'manual_edit'
            """)
    int deleteManualByAreaId(@Param("areaId") UUID areaId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from FloorAreaPolygonEntity p
            where p.floorArea.areaId = :areaId
              and p.markSessionId like 'edge_width:%'
            """)
    int deleteEdgeWidthByAreaId(@Param("areaId") UUID areaId);
}
