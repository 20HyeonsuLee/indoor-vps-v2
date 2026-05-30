package kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapEdgeRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class DeleteEdgeUseCase {

    private final MapEdgeRepository edgeRepository;

    public DeleteEdgeUseCase(MapEdgeRepository edgeRepository) {
        this.edgeRepository = edgeRepository;
    }

    @Transactional
    public void delete(UUID edgeId) {
        if (!edgeRepository.existsById(edgeId)) {
            throw new ClientApiException(
                    HttpStatus.NOT_FOUND, "EDGE_NOT_FOUND", "edge not found: " + edgeId);
        }
        edgeRepository.deleteById(edgeId);
    }
}
