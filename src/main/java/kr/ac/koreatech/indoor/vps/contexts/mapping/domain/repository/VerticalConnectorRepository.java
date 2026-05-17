package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerticalConnectorRepository extends JpaRepository<VerticalConnectorEntity, UUID> {

    java.util.Optional<VerticalConnectorEntity> findByBuilding_BuildingIdAndConnectorTypeAndConnectorKey(
            UUID buildingId, String connectorType, String connectorKey);


    @Query(value = """
            SELECT
                vc.connector_id                                      AS passageId,
                vc.building_id                                       AS buildingId,
                vc.connector_type                                    AS connectorType,
                vc.connector_key                                     AS connectorKey,
                vc.name                                              AS name,
                vc.is_mock                                           AS mock,
                vcs.connector_stop_id                                AS stopId,
                bf.name                                              AS levelId,
                COALESCE(vcs.route_node_id, p.route_node_id)        AS routeNodeId,
                bf.floor_id                                          AS floorId,
                ST_X(COALESCE(p.display_point, mn.geom))            AS x,
                ST_Y(COALESCE(p.display_point, mn.geom))            AS y
            FROM vertical_connector vc
            LEFT JOIN vertical_connector_stop vcs ON vcs.connector_id = vc.connector_id
            LEFT JOIN floor_area fa ON fa.area_id = vcs.area_id
            LEFT JOIN building_floor bf ON bf.floor_id = fa.floor_id
            LEFT JOIN poi_canonical p ON p.canonical_id = vcs.poi_canonical_id
            LEFT JOIN map_node mn ON mn.node_id = COALESCE(vcs.route_node_id, p.route_node_id)
            WHERE vc.building_id = :buildingId
            ORDER BY vc.connector_type, vc.connector_key, fa.area_index
            """, nativeQuery = true)
    List<PassageRow> findPassageRowsByBuildingId(@Param("buildingId") UUID buildingId);

    interface PassageRow {
        UUID getPassageId();
        UUID getBuildingId();
        String getConnectorType();
        String getConnectorKey();
        String getName();
        boolean isMock();
        UUID getStopId();
        String getLevelId();
        UUID getRouteNodeId();
        UUID getFloorId();
        Double getX();
        Double getY();
    }
}
