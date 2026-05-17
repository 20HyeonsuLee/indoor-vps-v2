package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.ScanIngestRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.StreamingScanStorage;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.StreamingScanStorage.StartedStreamingScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class StartStreamingScanUseCase {

    private final FloorUseCase floorService;
    private final ScanIngestRepository scanIngestRepository;
    private final StreamingScanStorage streamingScanStorage;
    private final ObjectMapper objectMapper;

    public StartStreamingScanUseCase(
            FloorUseCase floorService,
            ScanIngestRepository scanIngestRepository,
            StreamingScanStorage streamingScanStorage,
            ObjectMapper objectMapper
    ) {
        this.floorService = floorService;
        this.scanIngestRepository = scanIngestRepository;
        this.streamingScanStorage = streamingScanStorage;
        this.objectMapper = objectMapper;
    }

    public StartedStreamingScan execute(UUID floorId, String scanIdText, String deviceInfo) {
        floorService.requireFloor(floorId);
        UUID scanId = parseOptional(scanIdText).orElseGet(UUID::randomUUID);
        if (scanIngestRepository.existsById(scanId)) {
            throw new ClientApiException(HttpStatus.CONFLICT, "SCAN_ALREADY_EXISTS", "scan_id already exists");
        }
        return streamingScanStorage.start(floorId, scanId, deviceInfoMap(deviceInfo));
    }

    private Optional<UUID> parseOptional(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new ClientApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SCAN_ID", "invalid scan_id");
        }
    }

    private Map<String, Object> deviceInfoMap(String deviceInfo) {
        if (deviceInfo == null || deviceInfo.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(deviceInfo, new TypeReference<>() {
            });
        } catch (JsonProcessingException ignored) {
            return Map.of("raw", deviceInfo);
        }
    }
}
