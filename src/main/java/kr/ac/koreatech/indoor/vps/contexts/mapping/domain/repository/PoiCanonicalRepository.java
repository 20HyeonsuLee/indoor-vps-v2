package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.PoiCanonicalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PoiCanonicalRepository extends JpaRepository<PoiCanonicalEntity, UUID> {
    List<PoiCanonicalEntity> findByBuilding_BuildingIdOrderByNameAscLabelAsc(UUID buildingId);

    @Query("""
            select p from PoiCanonicalEntity p
            where p.building.buildingId = :buildingId
              and lower(concat(coalesce(p.name, ''), ' ', coalesce(p.label, ''), ' ', coalesce(p.category, ''))) like :query
            order by p.name asc nulls last, p.label asc nulls last
            """)
    List<PoiCanonicalEntity> search(
            @Param("buildingId") UUID buildingId,
            @Param("query") String query
    );
}
