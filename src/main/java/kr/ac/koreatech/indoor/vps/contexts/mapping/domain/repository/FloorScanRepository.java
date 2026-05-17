package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FloorScanRepository extends JpaRepository<FloorScanEntity, UUID> {
    List<FloorScanEntity> findByFloor_FloorIdOrderByUploadOrderAscCreatedAtAsc(UUID floorId);

    List<FloorScanEntity> findByArea_AreaIdOrderByUploadOrderAscCreatedAtAsc(UUID areaId);

    Optional<FloorScanEntity> findByFloor_FloorIdAndFloorScanId(UUID floorId, UUID floorScanId);

    Optional<FloorScanEntity> findByFloor_FloorIdAndScan_ScanId(UUID floorId, UUID scanId);

    Optional<FloorScanEntity> findFirstByFloor_FloorIdAndActiveTrueOrderByCreatedAtDesc(UUID floorId);

    Optional<FloorScanEntity> findFirstByArea_AreaIdAndActiveTrueOrderByCreatedAtDesc(UUID areaId);

    boolean existsByScan_ScanId(UUID scanId);

    @Query("""
            select fs from FloorScanEntity fs
            join fetch fs.floor f
            join fetch fs.scan s
            where f.building.buildingId = :buildingId
              and fs.active = true
            order by f.level asc, fs.createdAt asc
            """)
    List<FloorScanEntity> findActiveForBuilding(@Param("buildingId") UUID buildingId);

    @Query("""
            select fs from FloorScanEntity fs
            join fetch fs.floor f
            join fetch fs.scan s
            where f.floorId = :floorId
              and (fs.floorScanId in :ids or s.scanId in :ids)
            order by fs.uploadOrder asc, fs.createdAt asc
            """)
    List<FloorScanEntity> findMergeSources(
            @Param("floorId") UUID floorId,
            @Param("ids") List<UUID> ids
    );

    @Query("select coalesce(max(fs.uploadOrder), 0) + 1 from FloorScanEntity fs where fs.floor.floorId = :floorId")
    int nextUploadOrder(@Param("floorId") UUID floorId);

    @Modifying
    @Query("update FloorScanEntity fs set fs.active = false where fs.floor.floorId = :floorId")
    int deactivateForFloor(@Param("floorId") UUID floorId);
}
