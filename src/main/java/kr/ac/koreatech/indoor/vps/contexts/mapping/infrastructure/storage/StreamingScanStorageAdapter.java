package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.StreamingScanStorage.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.StreamingScanStorage;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage.ScanFileIo.StoredFile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class StreamingScanStorageAdapter implements StreamingScanStorage {
    private final IndoorProperties properties;
    private final ObjectMapper objectMapper;
    private final ScanDbWriter dbWriter;
    private final ScanFileIo fileIo;
    private final ScanStateWriter stateWriter;

    public StreamingScanStorageAdapter(
            IndoorProperties properties,
            ObjectMapper objectMapper,
            ScanDbWriter dbWriter,
            ScanFileIo fileIo,
            ScanStateWriter stateWriter
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.dbWriter = dbWriter;
        this.fileIo = fileIo;
        this.stateWriter = stateWriter;
    }

    @Override
    public StartedStreamingScan start(UUID floorId, UUID scanId, Map<String, Object> deviceInfo) {
        try {
            Files.createDirectories(framesDir(scanId));
            Files.createDirectories(linksDir(scanId));
            RtabmapDbInitializer.initialize(rtabmapDbPath(scanId));
            stateWriter.writeState(statePath(scanId), floorId, scanId, storagePath(scanId), deviceInfo, "STARTED", 0, 0, null);
            return new StartedStreamingScan(scanId, floorId, storagePath(scanId), "STARTED");
        } catch (IOException | SQLException e) {
            throw new ClientApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STREAMING_SCAN_START_FAILED", e.getMessage());
        }
    }

    @Override
    public StreamingFrameStats append(UUID scanId, ScanFramesRequest request) {
        StreamingState state = requireState(scanId);
        int framesApplied = 0;
        int framesSkipped = 0;
        int linksApplied = 0;
        int linksSkipped = 0;
        List<FramePayload> frames = request.frames() == null ? List.of() : request.frames();
        List<FrameLinkPayload> links = request.links() == null ? List.of() : request.links();
        try {
            Files.createDirectories(framesDir(scanId));
            Files.createDirectories(linksDir(scanId));
            RtabmapDbInitializer.initialize(rtabmapDbPath(scanId));
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + rtabmapDbPath(scanId))) {
                for (FramePayload frame : frames) {
                    Path framePath = framesDir(scanId).resolve("%010d.json".formatted(frame.nodeId()));
                    if (!Files.exists(framePath)) {
                        stateWriter.writeJson(framePath, frame);
                    }
                    if (dbWriter.insertFrame(connection, frame)) {
                        framesApplied++;
                    } else {
                        framesSkipped++;
                    }
                }
                for (FrameLinkPayload link : links) {
                    Path linkPath = linksDir(scanId).resolve(linkKey(link) + ".json");
                    if (!Files.exists(linkPath)) {
                        stateWriter.writeJson(linkPath, link);
                    }
                    if (dbWriter.insertLink(connection, link)) {
                        linksApplied++;
                    } else {
                        linksSkipped++;
                    }
                }
            }
            int nodeCount = ScanDbStats.countTableRows(rtabmapDbPath(scanId), "Node").orElse(0);
            int lastNodeId = ScanDbStats.maxNodeId(rtabmapDbPath(scanId)).orElse(0);
            stateWriter.writeState(statePath(scanId), state.floorId(), scanId, storagePath(scanId),
                    state.deviceInfo(), "STARTED", lastNodeId, nodeCount, null);
            return new StreamingFrameStats(scanId, framesApplied, framesSkipped, linksApplied, linksSkipped, lastNodeId, nodeCount);
        } catch (IOException | SQLException e) {
            throw new ClientApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STREAMING_FRAME_STORE_FAILED", e.getMessage());
        }
    }

    @Override
    public FinalizedStreamingScan finalizeScan(UUID scanId, MultipartFile manifest, MultipartFile metadata) {
        StreamingState state = requireState(scanId);
        requireFile(manifest, "MANIFEST_REQUIRED", "manifest is required");
        requireFile(metadata, "METADATA_REQUIRED", "metadata is required");
        Path root = scanRoot(scanId);
        try {
            Files.createDirectories(root);
            fileIo.copy(manifest, root.resolve("manifest.json"));
            fileIo.copy(metadata, root.resolve("scan_metadata.db"));
            RtabmapDbInitializer.checkpoint(rtabmapDbPath(scanId));
            StoredFile rtabmapFile = fileIo.hashFile(rtabmapDbPath(scanId));

            int nodeCount = ScanDbStats.countTableRows(rtabmapDbPath(scanId), "Node").orElse(0);
            int keyframeCount = ScanDbStats.countTableRows(root.resolve("scan_metadata.db"), "keyframe_meta")
                    .or(() -> ScanDbStats.manifestCount(root.resolve("manifest.json"), "sidecar_keyframe_meta_count", objectMapper))
                    .orElse(nodeCount);
            int poiMarkCount = ScanDbStats.countTableRows(root.resolve("scan_metadata.db"), "poi_mark").orElse(0);
            stateWriter.writeState(statePath(scanId), state.floorId(), scanId, storagePath(scanId),
                    state.deviceInfo(), "READY",
                    ScanDbStats.maxNodeId(rtabmapDbPath(scanId)).orElse(0), nodeCount, Instant.now().toString());
            return new FinalizedStreamingScan(
                    scanId, state.floorId(), storagePath(scanId),
                    rtabmapFile.sha256(), rtabmapFile.size(),
                    nodeCount, keyframeCount, poiMarkCount, state.deviceInfo()
            );
        } catch (ClientApiException e) {
            throw e;
        } catch (IOException | SQLException e) {
            throw new ClientApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STREAMING_FINALIZE_FAILED", e.getMessage());
        }
    }

    private StreamingState requireState(UUID scanId) {
        Path path = statePath(scanId);
        if (!Files.exists(path)) {
            throw new ClientApiException(HttpStatus.NOT_FOUND, "SCAN_SESSION_NOT_FOUND", "streaming scan session not found");
        }
        try {
            JsonNode json = objectMapper.readTree(path.toFile());
            Map<String, Object> deviceInfo = null;
            if (json.hasNonNull("deviceInfo")) {
                deviceInfo = objectMapper.convertValue(json.path("deviceInfo"), new TypeReference<>() {
                });
            }
            return new StreamingState(UUID.fromString(json.path("floorId").asText()), deviceInfo);
        } catch (IOException | IllegalArgumentException e) {
            throw new ClientApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STREAMING_STATE_INVALID", e.getMessage());
        }
    }

    private void requireFile(MultipartFile file, String code, String message) {
        if (file == null || file.isEmpty()) {
            throw new ClientApiException(HttpStatus.BAD_REQUEST, code, message);
        }
    }

    private String linkKey(FrameLinkPayload link) {
        int type = link.type() == null ? 0 : link.type();
        return "%010d-%010d-%03d".formatted(link.fromId(), link.toId(), type);
    }

    private Path statePath(UUID scanId) {
        return scanRoot(scanId).resolve("streaming").resolve("state.json");
    }

    private Path framesDir(UUID scanId) {
        return scanRoot(scanId).resolve("streaming").resolve("frames");
    }

    private Path linksDir(UUID scanId) {
        return scanRoot(scanId).resolve("streaming").resolve("links");
    }

    private Path rtabmapDbPath(UUID scanId) {
        return scanRoot(scanId).resolve("rtabmap.db");
    }

    private Path scanRoot(UUID scanId) {
        return properties.getStorageRoot().toAbsolutePath().normalize().resolve("scans").resolve(scanId.toString());
    }

    private String storagePath(UUID scanId) {
        return "scans/" + scanId;
    }

    private record StreamingState(UUID floorId, Map<String, Object> deviceInfo) {
    }
}
