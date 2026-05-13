package kr.ac.koreatech.indoor.vps.api;

import static kr.ac.koreatech.indoor.vps.api.dto.ScanDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.application.scan.ScanApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "스캔/처리")
public class ScanController {
    private final ScanApplicationService service;

    public ScanController(ScanApplicationService service) {
        this.service = service;
    }

    @PostMapping("/floors/{floorId}/scans/chunks")
    @ResponseStatus(HttpStatus.CREATED)
    public ScanChunkResponse uploadScanChunk(
            @PathVariable UUID floorId,
            @RequestParam(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "payload", required = false) MultipartFile payload,
            @RequestParam(name = "scan_id", required = false) String scanId,
            @RequestParam(name = "device_info", required = false) String deviceInfo,
            @RequestParam(name = "force", defaultValue = "false") boolean force
    ) {
        MultipartFile upload = file != null ? file : payload;
        if (upload == null || upload.isEmpty()) {
            throw new ClientApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "file or payload is required");
        }
        return service.uploadScanChunk(floorId, upload, scanId, deviceInfo, force);
    }

    @GetMapping("/floors/{floorId}/scans/chunks")
    public List<ScanChunkResponse> listScanChunks(@PathVariable UUID floorId) {
        return service.listScanChunks(floorId);
    }

    @DeleteMapping("/floors/{floorId}/scans/chunks/{chunkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScanChunk(@PathVariable UUID floorId, @PathVariable UUID chunkId) {
        service.deleteScanChunk(floorId, chunkId);
    }

    @PostMapping("/floors/{floorId}/scans/merge")
    public MergedScanResponse mergeScans(@PathVariable UUID floorId, @RequestBody MergeScansRequest request) {
        return service.mergeScans(floorId, request.chunkIds());
    }

    @GetMapping("/floors/{floorId}/scans/merge/status")
    public MergedScanResponse getMergeStatus(@PathVariable UUID floorId) {
        return service.mergeStatus(floorId);
    }

    @PostMapping("/floors/{floorId}/process")
    public ProcessingStatusResponse processFloor(@PathVariable UUID floorId) {
        return service.process(floorId);
    }

    @GetMapping("/floors/{floorId}/process/status")
    public ProcessingStatusResponse getProcessStatus(@PathVariable UUID floorId) {
        return service.processStatus(floorId);
    }
}
