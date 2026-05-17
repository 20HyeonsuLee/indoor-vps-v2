package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.ScanDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.ScanApplicationService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.capture.FixtureCaptureService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.capture.FixtureCaptureService.CaptureRecord;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final FixtureCaptureService fixtureCapture;

    public ScanController(ScanApplicationService service, FixtureCaptureService fixtureCapture) {
        this.service = service;
        this.fixtureCapture = fixtureCapture;
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
        CaptureRecord capture = fixtureCapture.captureScanChunk(floorId, upload, scanId, deviceInfo, force);
        try {
            ScanChunkResponse response = service.uploadScanChunk(floorId, upload, scanId, deviceInfo, force);
            fixtureCapture.writeResponse(capture, response);
            return response;
        } catch (RuntimeException e) {
            fixtureCapture.writeError(capture, e);
            throw e;
        }
    }

    @PostMapping("/floors/{floorId}/scans/start")
    @ResponseStatus(HttpStatus.CREATED)
    public ScanStartResponse startStreamingScan(
            @PathVariable UUID floorId,
            @RequestBody(required = false) ScanStartRequest request
    ) {
        return service.startStreamingScan(floorId, request);
    }

    @PostMapping("/scans/{scanId}/frames")
    public ScanFramesResponse pushStreamingFrames(
            @PathVariable UUID scanId,
            @RequestBody ScanFramesRequest request
    ) {
        return service.pushStreamingFrames(scanId, request);
    }

    @PostMapping(value = "/scans/{scanId}/finalize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ScanFinalizeResponse finalizeStreamingScan(
            @PathVariable UUID scanId,
            @RequestParam("manifest") MultipartFile manifest,
            @RequestParam("metadata") MultipartFile metadata
    ) {
        return service.finalizeStreamingScan(scanId, manifest, metadata);
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

    @PostMapping("/floors/{floorId}/build")
    public ProcessingStatusResponse buildFloor(@PathVariable UUID floorId) {
        return service.process(floorId);
    }

    @GetMapping("/floors/{floorId}/process/status")
    public ProcessingStatusResponse getProcessStatus(@PathVariable UUID floorId) {
        return service.processStatus(floorId);
    }
}
