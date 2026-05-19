package kr.ac.koreatech.indoor.vps.contexts.mapping.application.building;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildingRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class BatchDeleteBuildingsUseCase {

    private final BuildingRepository buildingRepository;

    public BatchDeleteBuildingsUseCase(BuildingRepository buildingRepository) {
        this.buildingRepository = buildingRepository;
    }

    @Transactional
    public void deleteAll(List<UUID> buildingIds) {
        if (buildingIds == null || buildingIds.isEmpty()) {
            return;
        }
        buildingRepository.deleteAllByIdInBatch(buildingIds);
    }
}
