package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BuildJobRepository extends JpaRepository<BuildJobEntity, UUID> {
    Optional<BuildJobEntity> findFirstByScan_ScanIdOrderByEnqueuedAtDesc(UUID scanId);

    @Query(value = """
            SELECT *
            FROM build_job
            WHERE state = 'pending'
            ORDER BY enqueued_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<BuildJobEntity> lockNextPendingJob();

    @Query(value = """
            SELECT *
            FROM build_job
            WHERE build_job_id = :buildJobId
              AND state = 'pending'
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<BuildJobEntity> lockPendingJobById(@Param("buildJobId") UUID buildJobId);
}
