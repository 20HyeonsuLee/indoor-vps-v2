package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.ScanDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.FinalizeStreamingScanUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.ListScanChunksUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.MergeScansUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.MergedScanResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.ProcessFloorUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.ProcessingStatusResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.PushStreamingFramesUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.ScanChunkResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.ScanFinalizeResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.StartStreamingScanUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.UploadScanChunkUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.StreamingScanStorage.ScanFramesRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.StreamingScanStorage.StartedStreamingScan;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.StreamingScanStorage.StreamingFrameStats;
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

    private final StartStreamingScanUseCase startStreamingScanUseCase;
    private final PushStreamingFramesUseCase pushStreamingFramesUseCase;
    private final FinalizeStreamingScanUseCase finalizeStreamingScanUseCase;
    private final UploadScanChunkUseCase uploadScanChunkUseCase;
    private final ListScanChunksUseCase listScanChunksUseCase;
    private final MergeScansUseCase mergeScansUseCase;
    private final ProcessFloorUseCase processFloorUseCase;
    private final FixtureCaptureService fixtureCapture;

    public ScanController(
            StartStreamingScanUseCase startStreamingScanUseCase,
            PushStreamingFramesUseCase pushStreamingFramesUseCase,
            FinalizeStreamingScanUseCase finalizeStreamingScanUseCase,
            UploadScanChunkUseCase uploadScanChunkUseCase,
            ListScanChunksUseCase listScanChunksUseCase,
            MergeScansUseCase mergeScansUseCase,
            ProcessFloorUseCase processFloorUseCase,
            FixtureCaptureService fixtureCapture
    ) {
        this.startStreamingScanUseCase = startStreamingScanUseCase;
        this.pushStreamingFramesUseCase = pushStreamingFramesUseCase;
        this.finalizeStreamingScanUseCase = finalizeStreamingScanUseCase;
        this.uploadScanChunkUseCase = uploadScanChunkUseCase;
        this.listScanChunksUseCase = listScanChunksUseCase;
        this.mergeScansUseCase = mergeScansUseCase;
        this.processFloorUseCase = processFloorUseCase;
        this.fixtureCapture = fixtureCapture;
    }

    @PostMapping("/floors/{floorId}/scans/chunks")
    @ResponseStatus(HttpStatus.CREATED)
    public ScanChunkResult uploadScanChunk(
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
            ScanChunkResult response = uploadScanChunkUseCase.execute(floorId, upload, scanId, deviceInfo, force);
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
        StartedStreamingScan started = startStreamingScanUseCase.execute(
                floorId,
                request == null ? null : request.scanId(),
                request == null ? null : request.deviceInfo()
        );
        return new ScanStartResponse(started.scanId(), started.floorId(), started.storagePath(), started.state());
    }

    @PostMapping("/scans/{scanId}/frames")
    public ScanFramesResponse pushStreamingFrames(
            @PathVariable UUID scanId,
            @RequestBody ScanFramesRequest request
    ) {
        StreamingFrameStats stats = pushStreamingFramesUseCase.execute(scanId, request);
        return new ScanFramesResponse(
                stats.scanId(),
                stats.framesApplied(),
                stats.framesSkipped(),
                stats.linksApplied(),
                stats.linksSkipped(),
                stats.lastNodeId(),
                stats.nodeCount()
        );
    }

    @PostMapping(value = "/scans/{scanId}/finalize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ScanFinalizeResult finalizeStreamingScan(
            @PathVariable UUID scanId,
            @RequestParam("manifest") MultipartFile manifest,
            @RequestParam("metadata") MultipartFile metadata
    ) {
        return finalizeStreamingScanUseCase.execute(scanId, manifest, metadata);
    }

    @GetMapping("/floors/{floorId}/scans/chunks")
    public List<ScanChunkResult> listScanChunks(@PathVariable UUID floorId) {
        return listScanChunksUseCase.listChunks(floorId);
    }

    @DeleteMapping("/floors/{floorId}/scans/chunks/{chunkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScanChunk(@PathVariable UUID floorId, @PathVariable UUID chunkId) {
        listScanChunksUseCase.deleteChunk(floorId, chunkId);
    }

    @PostMapping("/floors/{floorId}/scans/merge")
    public MergedScanResult mergeScans(@PathVariable UUID floorId, @RequestBody MergeScansRequest request) {
        return mergeScansUseCase.merge(floorId, request.chunkIds());
    }

    @GetMapping("/floors/{floorId}/scans/merge/status")
    public MergedScanResult getMergeStatus(@PathVariable UUID floorId) {
        return mergeScansUseCase.mergeStatus(floorId);
    }

    @PostMapping("/floors/{floorId}/process")
    public ProcessingStatusResult processFloor(@PathVariable UUID floorId) {
        return processFloorUseCase.process(floorId);
    }

    @PostMapping("/floors/{floorId}/build")
    public ProcessingStatusResult buildFloor(@PathVariable UUID floorId) {
        return processFloorUseCase.process(floorId);
    }

    @GetMapping("/floors/{floorId}/process/status")
    public ProcessingStatusResult getProcessStatus(@PathVariable UUID floorId) {
        return processFloorUseCase.processStatus(floorId);
    }
}
