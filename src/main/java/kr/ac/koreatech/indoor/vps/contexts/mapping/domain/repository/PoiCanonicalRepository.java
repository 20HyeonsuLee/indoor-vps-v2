package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.PoiCanonicalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PoiCanonicalRepository extends JpaRepository<PoiCanonicalEntity, UUID> {
    List<PoiCanonicalEntity> findByScanIdOrderByCanonicalId(UUID scanId);

    java.util.Optional<PoiCanonicalEntity> findByCanonicalIdAndBuilding_BuildingId(
            UUID canonicalId, UUID buildingId
    );

    /**
     * active scan에 속한 POI만 반환. 머지/재스캔으로 비활성화된 스캔의 POI는
     * pathfinding 그래프에 노드가 없어 ROUTE_NODE_NOT_FOUND 발생 — 일관성을
     * 위해 list/search 결과에서 제외.
     */
    @Query("""
            select p from PoiCanonicalEntity p
            where p.building.buildingId = :buildingId
              and exists (
                select 1 from FloorScanEntity fs
                where fs.scan.scanId = p.scanId
                  and fs.active = true
              )
            order by p.name asc nulls last, p.label asc nulls last
            """)
    List<PoiCanonicalEntity> findActiveByBuilding(@Param("buildingId") UUID buildingId);

    @Query("""
            select p from PoiCanonicalEntity p
            where p.building.buildingId = :buildingId
              and exists (
                select 1 from FloorScanEntity fs
                where fs.scan.scanId = p.scanId
                  and fs.active = true
              )
              and lower(concat(coalesce(p.name, ''), ' ', coalesce(p.label, ''), ' ', coalesce(p.category, ''))) like :query
            order by p.name asc nulls last, p.label asc nulls last
            """)
    List<PoiCanonicalEntity> searchActive(
            @Param("buildingId") UUID buildingId,
            @Param("query") String query
    );
}
