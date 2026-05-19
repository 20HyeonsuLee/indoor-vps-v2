package kr.ac.koreatech.indoor.vps.contexts.mapping.application.building;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildingRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좌표 근접 카메라 프레임 조회. RTAB-Map sqlite 어댑터 통합 전까지는 빈 리스트 반환 — 클라이언트
 * NearbyImagesPanel은 결과 없으면 패널만 비우므로 정합성 유지됨.
 */
@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class NodeImagesUseCase {

    private final BuildingRepository buildingRepository;

    public NodeImagesUseCase(BuildingRepository buildingRepository) {
        this.buildingRepository = buildingRepository;
    }

    public List<NodeImageResult> findNearby(UUID buildingId, double x, double y, double z) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new ClientApiException(
                    HttpStatus.NOT_FOUND, "BUILDING_NOT_FOUND", "building not found: " + buildingId);
        }
        return List.of();
    }
}
