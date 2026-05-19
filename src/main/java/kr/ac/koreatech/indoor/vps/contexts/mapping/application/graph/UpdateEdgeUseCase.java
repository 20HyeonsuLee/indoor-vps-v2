package kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapEdgeRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class UpdateEdgeUseCase {

    private final MapEdgeRepository edgeRepository;

    public UpdateEdgeUseCase(MapEdgeRepository edgeRepository) {
        this.edgeRepository = edgeRepository;
    }

    @Transactional
    public EdgeResult update(UUID edgeId, UpdateEdgeCommand command) {
        MapEdgeEntity edge = edgeRepository.findById(edgeId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "EDGE_NOT_FOUND", "edge not found: " + edgeId));
        command.edgeType().ifPresent(edge::changeEdgeType);
        return EdgeResult.from(edgeRepository.saveAndFlush(edge));
    }
}
