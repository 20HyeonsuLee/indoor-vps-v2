package kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapEdgeRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapNodeRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class DeleteNodeUseCase {

    private final MapNodeRepository nodeRepository;
    private final MapEdgeRepository edgeRepository;

    public DeleteNodeUseCase(MapNodeRepository nodeRepository, MapEdgeRepository edgeRepository) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
    }

    @Transactional
    public void delete(UUID nodeId) {
        if (!nodeRepository.existsById(nodeId)) {
            throw new ClientApiException(
                    HttpStatus.NOT_FOUND, "NODE_NOT_FOUND", "node not found: " + nodeId);
        }
        edgeRepository.deleteByConnectedNodeId(nodeId);
        nodeRepository.deleteById(nodeId);
    }
}
