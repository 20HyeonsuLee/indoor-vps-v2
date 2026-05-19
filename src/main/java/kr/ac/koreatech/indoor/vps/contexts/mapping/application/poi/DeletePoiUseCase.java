package kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.PoiCanonicalRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class DeletePoiUseCase {

    private final PoiCanonicalRepository poiRepository;

    public DeletePoiUseCase(PoiCanonicalRepository poiRepository) {
        this.poiRepository = poiRepository;
    }

    @Transactional
    public void delete(UUID poiId) {
        if (!poiRepository.existsById(poiId)) {
            throw new ClientApiException(
                    HttpStatus.NOT_FOUND, "POI_NOT_FOUND", "poi not found: " + poiId);
        }
        poiRepository.deleteById(poiId);
    }
}
