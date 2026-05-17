package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.ScanIngestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanIngestRepository extends JpaRepository<ScanIngestEntity, UUID> {
}
