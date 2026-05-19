package kr.ac.koreatech.indoor.vps.contexts.mapping.application.polygon;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorAreaPolygonRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class DeletePolygonUseCase {

    private final FloorAreaPolygonRepository polygonRepository;

    public DeletePolygonUseCase(FloorAreaPolygonRepository polygonRepository) {
        this.polygonRepository = polygonRepository;
    }

    @Transactional
    public void delete(UUID polygonId) {
        if (!polygonRepository.existsById(polygonId)) {
            throw new ClientApiException(
                    HttpStatus.NOT_FOUND, "POLYGON_NOT_FOUND", "polygon not found: " + polygonId);
        }
        polygonRepository.deleteById(polygonId);
    }
}
