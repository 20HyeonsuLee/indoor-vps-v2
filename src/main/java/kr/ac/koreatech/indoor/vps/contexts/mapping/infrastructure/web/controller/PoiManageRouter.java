package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi.CreateManualPoiUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi.CreatePoiCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi.DeletePoiUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi.UpdatePoiCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi.UpdatePoiUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.PoiCanonicalEntity;
import org.springframework.stereotype.Component;

/**
 * PoiController가 4개 UseCase를 직접 주입하면 instance_vars 제약 초과 → 라우터로 묶음.
 */
@Component
class PoiManageRouter {

    private final CreateManualPoiUseCase createUseCase;
    private final UpdatePoiUseCase updateUseCase;
    private final DeletePoiUseCase deleteUseCase;

    PoiManageRouter(
            CreateManualPoiUseCase createUseCase,
            UpdatePoiUseCase updateUseCase,
            DeletePoiUseCase deleteUseCase
    ) {
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
    }

    PoiCanonicalEntity create(UUID buildingId, CreatePoiCommand command) {
        return createUseCase.create(buildingId, command);
    }

    PoiCanonicalEntity update(UUID poiId, UpdatePoiCommand command) {
        return updateUseCase.update(poiId, command);
    }

    void delete(UUID poiId) {
        deleteUseCase.delete(poiId);
    }
}
