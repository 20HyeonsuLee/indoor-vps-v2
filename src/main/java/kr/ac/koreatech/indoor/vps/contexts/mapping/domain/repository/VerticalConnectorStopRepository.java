package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorStopEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerticalConnectorStopRepository extends JpaRepository<VerticalConnectorStopEntity, UUID> {

    Optional<VerticalConnectorStopEntity> findByConnector_ConnectorIdAndArea_AreaId(UUID connectorId, UUID areaId);

    @Query("""
            select s from VerticalConnectorStopEntity s
              join fetch s.connector c
              join fetch s.area a
              join fetch a.floor f
            where c.building.buildingId = :buildingId
            """)
    List<VerticalConnectorStopEntity> findByConnector_Building_BuildingId(@Param("buildingId") UUID buildingId);

    @Query("""
            select s from VerticalConnectorStopEntity s
              join fetch s.connector c
              join fetch s.area a
              join fetch a.floor f
            where a.areaId = :areaId
            """)
    List<VerticalConnectorStopEntity> findByArea_AreaId(@Param("areaId") UUID areaId);
}
