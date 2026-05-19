package kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapEdgeRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapNodeRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class ClearManualGraphUseCase {

    private final MapNodeRepository nodeRepository;
    private final MapEdgeRepository edgeRepository;

    public ClearManualGraphUseCase(MapNodeRepository nodeRepository, MapEdgeRepository edgeRepository) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
    }

    /**
     * 수동(`source_ref->>'origin'='manual_edit'`)으로 추가된 노드/엣지만 삭제.
     * RTAB-Map 자동 생성 그래프는 보존.
     * 순서: manual 노드와 연결된 모든 엣지 먼저 삭제(자동/수동 모두) → manual 노드 삭제.
     */
    @Transactional
    public void clear(UUID areaId) {
        edgeRepository.deleteEdgesTouchingManualNodesByAreaId(areaId);
        nodeRepository.deleteManualByAreaId(areaId);
    }
}
