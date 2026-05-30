package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapEdgeRepository extends JpaRepository<MapEdgeEntity, UUID> {
    List<MapEdgeEntity> findByScanIdAndStaleFalseOrderByEdgeId(UUID scanId);

    long deleteByScanId(UUID scanId);

    List<MapEdgeEntity> findByAreaIdAndStaleFalseOrderByEdgeId(UUID areaId);

    @Query("""
            select e from MapEdgeEntity e
            where e.fromNodeId = :nodeId or e.toNodeId = :nodeId
            """)
    List<MapEdgeEntity> findByConnectedNodeId(@Param("nodeId") UUID nodeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MapEdgeEntity e where e.fromNodeId = :nodeId or e.toNodeId = :nodeId")
    int deleteByConnectedNodeId(@Param("nodeId") UUID nodeId);

    @Modifying
    @Query(value = """
            delete from map_edge
            where area_id = :areaId
              and exists (
                select 1 from map_node n
                where n.node_id in (map_edge.from_node_id, map_edge.to_node_id)
                  and n.source_ref ->> 'origin' = 'manual_edit'
              )
            """, nativeQuery = true)
    int deleteEdgesTouchingManualNodesByAreaId(@Param("areaId") UUID areaId);
}
