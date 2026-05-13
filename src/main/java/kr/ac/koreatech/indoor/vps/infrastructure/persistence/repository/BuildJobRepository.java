package kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.DbEnums.BuildState;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.BuildJobEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildJobRepository extends JpaRepository<BuildJobEntity, UUID> {
    Optional<BuildJobEntity> findFirstByScan_ScanIdOrderByEnqueuedAtDesc(UUID scanId);

    List<BuildJobEntity> findByStateOrderByEnqueuedAtAsc(BuildState state, Limit limit);
}
