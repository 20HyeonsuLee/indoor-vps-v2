package kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.ScanIngestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanIngestRepository extends JpaRepository<ScanIngestEntity, UUID> {
}
