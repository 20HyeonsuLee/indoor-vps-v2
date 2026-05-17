package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository;

import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorStopEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerticalConnectorStopRepository extends JpaRepository<VerticalConnectorStopEntity, UUID> {

    Optional<VerticalConnectorStopEntity> findByConnector_ConnectorIdAndLevelId(UUID connectorId, String levelId);
}
