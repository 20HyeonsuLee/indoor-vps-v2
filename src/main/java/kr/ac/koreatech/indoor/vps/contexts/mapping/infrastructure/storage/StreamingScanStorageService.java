package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.ScanDtos.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class StreamingScanStorageService {
    private static final Base64.Decoder BASE64 = Base64.getDecoder();
    private static final String RTABMAP_PARAMS = "Mem/IncrementalMemory=true;Rtabmap/DetectionRate=0;";

    private final IndoorProperties properties;
    private final ObjectMapper objectMapper;

    public StreamingScanStorageService(IndoorProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public StartedStreamingScan start(UUID floorId, UUID scanId, Map<String, Object> deviceInfo) {
        try {
            Files.createDirectories(framesDir(scanId));
            Files.createDirectories(linksDir(scanId));
            initializeRtabmapDb(rtabmapDbPath(scanId));
            writeJson(statePath(scanId), stateBody(
                    floorId,
                    scanId,
                    deviceInfo,
                    "STARTED",
                    0,
                    0,
                    null
            ));
            return new StartedStreamingScan(scanId, floorId, storagePath(scanId), "STARTED");
        } catch (IOException | SQLException e) {
            throw new ClientApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STREAMING_SCAN_START_FAILED", e.getMessage());
        }
    }

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
            initializeRtabmapDb(rtabmapDbPath(scanId));
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + rtabmapDbPath(scanId))) {
                for (FramePayload frame : frames) {
                    Path framePath = framesDir(scanId).resolve("%010d.json".formatted(frame.nodeId()));
                    if (!Files.exists(framePath)) {
                        writeJson(framePath, frame);
                    }
                    if (insertFrame(connection, frame)) {
                        framesApplied++;
                        continue;
                    }
                    framesSkipped++;
                }
                for (FrameLinkPayload link : links) {
                    Path linkPath = linksDir(scanId).resolve(linkKey(link) + ".json");
                    if (!Files.exists(linkPath)) {
                        writeJson(linkPath, link);
                    }
                    if (insertLink(connection, link)) {
                        linksApplied++;
                        continue;
                    }
                    linksSkipped++;
                }
            }
            int nodeCount = countRows(rtabmapDbPath(scanId), "Node").orElse(0);
            int lastNodeId = maxNodeIdFromDb(scanId).orElse(0);
            writeJson(statePath(scanId), stateBody(
                    state.floorId(),
                    scanId,
                    state.deviceInfo(),
                    "STARTED",
                    lastNodeId,
                    nodeCount,
                    null
            ));
            return new StreamingFrameStats(
                    scanId,
                    framesApplied,
                    framesSkipped,
                    linksApplied,
                    linksSkipped,
                    lastNodeId,
                    nodeCount
            );
        } catch (IOException | SQLException e) {
            throw new ClientApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STREAMING_FRAME_STORE_FAILED", e.getMessage());
        }
    }

    public FinalizedStreamingScan finalizeScan(UUID scanId, MultipartFile manifest, MultipartFile metadata) {
        StreamingState state = requireState(scanId);
        requireFile(manifest, "MANIFEST_REQUIRED", "manifest is required");
        requireFile(metadata, "METADATA_REQUIRED", "metadata is required");
        Path root = scanRoot(scanId);
        try {
            Files.createDirectories(root);
            copy(manifest, root.resolve("manifest.json"));
            copy(metadata, root.resolve("scan_metadata.db"));
            checkpointRtabmapDb(rtabmapDbPath(scanId));
            StoredFile rtabmapFile = hashFile(rtabmapDbPath(scanId));

            int nodeCount = countRows(rtabmapDbPath(scanId), "Node").orElse(0);
            int keyframeCount = countTableRows(root.resolve("scan_metadata.db"), "keyframe_meta")
                    .or(() -> manifestCount(root.resolve("manifest.json"), "sidecar_keyframe_meta_count"))
                    .orElse(nodeCount);
            int poiMarkCount = countTableRows(root.resolve("scan_metadata.db"), "poi_mark").orElse(0);
            writeJson(statePath(scanId), stateBody(
                    state.floorId(),
                    scanId,
                    state.deviceInfo(),
                    "READY",
                    maxNodeIdFromDb(scanId).orElse(0),
                    nodeCount,
                    Instant.now().toString()
            ));
            return new FinalizedStreamingScan(
                    scanId,
                    state.floorId(),
                    storagePath(scanId),
                    rtabmapFile.sha256(),
                    rtabmapFile.size(),
                    nodeCount,
                    keyframeCount,
                    poiMarkCount,
                    state.deviceInfo()
            );
        } catch (ClientApiException e) {
            throw e;
        } catch (IOException | SQLException e) {
            throw new ClientApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STREAMING_FINALIZE_FAILED", e.getMessage());
        }
    }

    private void initializeRtabmapDb(Path dbPath) throws IOException, SQLException {
        Files.createDirectories(dbPath.getParent());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS Node (
                            id INTEGER NOT NULL,
                            map_id INTEGER NOT NULL,
                            weight INTEGER,
                            stamp FLOAT,
                            pose BLOB NOT NULL,
                            ground_truth_pose BLOB,
                            velocity BLOB,
                            label TEXT,
                            gps BLOB,
                            env_sensors BLOB,
                            time_enter DATE,
                            PRIMARY KEY (id)
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS Data (
                            id INTEGER NOT NULL,
                            image BLOB,
                            depth BLOB,
                            depth_confidence BLOB,
                            calibration BLOB,
                            scan BLOB,
                            scan_info BLOB,
                            ground_cells BLOB,
                            obstacle_cells BLOB,
                            empty_cells BLOB,
                            cell_size FLOAT,
                            view_point_x FLOAT,
                            view_point_y FLOAT,
                            view_point_z FLOAT,
                            user_data BLOB,
                            time_enter DATE,
                            PRIMARY KEY (id)
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS Link (
                            from_id INTEGER NOT NULL,
                            to_id INTEGER NOT NULL,
                            type INTEGER NOT NULL,
                            information_matrix BLOB NOT NULL,
                            transform BLOB,
                            user_data BLOB
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS Word (
                            id INTEGER NOT NULL,
                            descriptor_size INTEGER NOT NULL,
                            descriptor BLOB NOT NULL,
                            time_enter DATE,
                            PRIMARY KEY (id)
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS Feature (
                            node_id INTEGER NOT NULL,
                            word_id INTEGER NOT NULL,
                            pos_x FLOAT NOT NULL,
                            pos_y FLOAT NOT NULL,
                            size INTEGER NOT NULL,
                            dir FLOAT NOT NULL,
                            response FLOAT NOT NULL,
                            octave INTEGER NOT NULL,
                            depth_x FLOAT,
                            depth_y FLOAT,
                            depth_z FLOAT,
                            descriptor_size INTEGER,
                            descriptor BLOB
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS GlobalDescriptor (
                            node_id INTEGER NOT NULL,
                            type INTEGER NOT NULL,
                            info BLOB,
                            data BLOB NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS Info (
                            STM_size INTEGER,
                            last_sign_added INTEGER,
                            process_mem_used INTEGER,
                            database_mem_used INTEGER,
                            dictionary_size INTEGER,
                            parameters TEXT,
                            time_enter DATE
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS Statistics (
                            id INTEGER NOT NULL,
                            stamp FLOAT,
                            data BLOB,
                            wm_state BLOB
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS Admin (
                            version TEXT,
                            preview_image BLOB,
                            opt_cloud BLOB,
                            opt_ids BLOB,
                            opt_poses BLOB,
                            opt_last_localization BLOB,
                            opt_polygons_size INTEGER,
                            opt_polygons BLOB,
                            opt_tex_coords BLOB,
                            opt_tex_materials BLOB,
                            opt_map BLOB,
                            opt_map_x_min FLOAT,
                            opt_map_y_min FLOAT,
                            opt_map_resolution FLOAT,
                            dictionary_index BLOB,
                            time_enter DATE
                        )
                        """);
                statement.execute("CREATE INDEX IF NOT EXISTS IDX_Feature_node_id on Feature (node_id)");
                statement.execute("CREATE INDEX IF NOT EXISTS IDX_GlobalDescriptor_node_id on GlobalDescriptor (node_id)");
                statement.execute("CREATE INDEX IF NOT EXISTS IDX_Link_from_id on Link (from_id)");
                statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_Link_unique on Link (from_id, to_id, type)");
                statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_Statistics_id on Statistics (id)");
            }
            ensureDataColumn(connection, "depth_confidence", "BLOB");
            ensureDataColumn(connection, "ground_cells", "BLOB");
            ensureDataColumn(connection, "obstacle_cells", "BLOB");
            ensureDataColumn(connection, "empty_cells", "BLOB");
            ensureDataColumn(connection, "cell_size", "FLOAT");
            ensureDataColumn(connection, "view_point_x", "FLOAT");
            ensureDataColumn(connection, "view_point_y", "FLOAT");
            ensureDataColumn(connection, "view_point_z", "FLOAT");
            ensureDataColumn(connection, "time_enter", "DATE");
            try (PreparedStatement adminInsert = connection.prepareStatement("""
                    INSERT INTO Admin(version, time_enter)
                    SELECT '0.23.5', datetime('now')
                    WHERE NOT EXISTS (SELECT 1 FROM Admin)
                    """);
                 PreparedStatement infoInsert = connection.prepareStatement("""
                         INSERT INTO Info(
                            STM_size, last_sign_added, process_mem_used, database_mem_used, dictionary_size, parameters, time_enter
                         )
                         SELECT 0, 0, 0, 0, 0, ?, datetime('now')
                         WHERE NOT EXISTS (SELECT 1 FROM Info)
                         """)) {
                adminInsert.executeUpdate();
                infoInsert.setString(1, RTABMAP_PARAMS);
                infoInsert.executeUpdate();
            }
        }
    }

    private void ensureDataColumn(Connection connection, String name, String definition) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(Data)");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                if (name.equalsIgnoreCase(result.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE Data ADD COLUMN " + name + " " + definition);
        }
    }

    private void checkpointRtabmapDb(Path dbPath) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(FULL)");
        }
    }

    private boolean insertFrame(Connection connection, FramePayload frame) throws SQLException {
        if (existsNode(connection, frame.nodeId())) {
            return false;
        }
        try (PreparedStatement nodeInsert = connection.prepareStatement("""
                INSERT INTO Node(id, stamp, map_id, weight, label, time_enter, pose, ground_truth_pose, velocity, gps, env_sensors)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL)
                """);
             PreparedStatement dataInsert = connection.prepareStatement("""
                     INSERT OR REPLACE INTO Data(id, image, depth, calibration, scan, scan_info, user_data)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            nodeInsert.setInt(1, frame.nodeId());
            nodeInsert.setDouble(2, frame.stamp());
            nodeInsert.setInt(3, frame.mapId() == null ? 0 : frame.mapId());
            nodeInsert.setInt(4, frame.weight() == null ? 0 : frame.weight());
            nodeInsert.setString(5, frame.label());
            nodeInsert.setLong(6, Math.round(frame.stamp() * 1000.0));
            nodeInsert.setBytes(7, decodePose(frame.pose()));
            nodeInsert.executeUpdate();

            dataInsert.setInt(1, frame.nodeId());
            dataInsert.setBytes(2, decodeOptionalBlob(frame.image()));
            dataInsert.setBytes(3, decodeOptionalBlob(frame.depth()));
            dataInsert.setBytes(4, decodeOptionalBlob(frame.calibration()));
            dataInsert.setBytes(5, decodeOptionalBlob(frame.scan()));
            dataInsert.setBytes(6, decodeOptionalBlob(frame.scanInfo()));
            dataInsert.setBytes(7, decodeOptionalBlob(frame.userData()));
            dataInsert.executeUpdate();
            return true;
        }
    }

    private boolean insertLink(Connection connection, FrameLinkPayload link) throws SQLException {
        try (PreparedStatement linkInsert = connection.prepareStatement("""
                INSERT OR IGNORE INTO Link(from_id, to_id, type, transform, information_matrix, user_data)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            linkInsert.setInt(1, link.fromId());
            linkInsert.setInt(2, link.toId());
            linkInsert.setInt(3, link.type() == null ? 0 : link.type());
            linkInsert.setBytes(4, decodeRequiredBlob(link.transform(), "INVALID_LINK_TRANSFORM", "link transform must be base64"));
            linkInsert.setBytes(5, decodeOptionalBlob(link.informationMatrix(), defaultInformationMatrix()));
            linkInsert.setBytes(6, decodeOptionalBlob(link.userData()));
            return linkInsert.executeUpdate() > 0;
        }
    }

    private boolean existsNode(Connection connection, int nodeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM Node WHERE id = ?")) {
            statement.setInt(1, nodeId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private byte[] decodePose(String encoded) {
        try {
            byte[] bytes = BASE64.decode(encoded == null ? "" : encoded);
            if (bytes.length != 48) {
                throw new IllegalArgumentException("expected 48 bytes, got " + bytes.length);
            }
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new ClientApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_FRAME_POSE",
                    "frame pose must be a base64-encoded 48-byte RTAB-Map pose blob"
            );
        }
    }

    private byte[] decodeOptionalBlob(String encoded) {
        return decodeOptionalBlob(encoded, null);
    }

    private byte[] decodeOptionalBlob(String encoded, byte[] fallback) {
        if (encoded == null || encoded.isBlank()) {
            return fallback;
        }
        return decodeRequiredBlob(encoded, "INVALID_FRAME_BLOB", "frame blob must be base64");
    }

    private byte[] defaultInformationMatrix() {
        ByteBuffer buffer = ByteBuffer.allocate(288).order(ByteOrder.LITTLE_ENDIAN);
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 6; col++) {
                buffer.putDouble(row == col ? 1.0 : 0.0);
            }
        }
        return buffer.array();
    }

    private byte[] decodeRequiredBlob(String encoded, String code, String message) {
        try {
            return BASE64.decode(encoded == null ? "" : encoded);
        } catch (IllegalArgumentException e) {
            throw new ClientApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
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

    private Map<String, Object> stateBody(
            UUID floorId,
            UUID scanId,
            Map<String, Object> deviceInfo,
            String state,
            int lastNodeId,
            int nodeCount,
            String finalizedAt
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("floorId", floorId);
        body.put("scanId", scanId);
        body.put("storagePath", storagePath(scanId));
        body.put("state", state);
        body.put("deviceInfo", deviceInfo);
        body.put("lastNodeId", lastNodeId);
        body.put("nodeCount", nodeCount);
        body.put("updatedAt", Instant.now().toString());
        if (finalizedAt != null) {
            body.put("finalizedAt", finalizedAt);
        }
        return body;
    }

    private StoredFile copy(MultipartFile file, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        MessageDigest digest = sha256();
        long size = 0;
        try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
                size += read;
            }
        }
        return new StoredFile(size, HexFormat.of().formatHex(digest.digest()));
    }

    private StoredFile hashFile(Path path) throws IOException {
        MessageDigest digest = sha256();
        long size = 0;
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                size += read;
            }
        }
        return new StoredFile(size, HexFormat.of().formatHex(digest.digest()));
    }

    private void writeJson(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    private Optional<Integer> countTableRows(Path sqlitePath, String tableName) {
        if (!Files.exists(sqlitePath)) {
            return Optional.empty();
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath)) {
            if (!tableExists(connection, tableName)) {
                return Optional.empty();
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                return result.next() ? Optional.of(result.getInt(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?"
        )) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private Optional<Integer> manifestCount(Path manifestPath, String field) {
        if (!Files.exists(manifestPath)) {
            return Optional.empty();
        }
        try {
            JsonNode json = objectMapper.readTree(manifestPath.toFile());
            return json.has(field) && json.path(field).canConvertToInt()
                    ? Optional.of(json.path(field).asInt())
                    : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<Integer> countRows(Path sqlitePath, String tableName) {
        return countTableRows(sqlitePath, tableName);
    }

    private Optional<Integer> maxNodeIdFromDb(UUID scanId) {
        Path dbPath = rtabmapDbPath(scanId);
        if (!Files.exists(dbPath)) {
            return Optional.empty();
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT MAX(id) FROM Node")) {
            return result.next() && result.getObject(1) != null ? Optional.of(result.getInt(1)) : Optional.empty();
        } catch (SQLException e) {
            return Optional.empty();
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

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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

    public record StartedStreamingScan(UUID scanId, UUID floorId, String storagePath, String state) {
    }

    public record StreamingFrameStats(
            UUID scanId,
            int framesApplied,
            int framesSkipped,
            int linksApplied,
            int linksSkipped,
            int lastNodeId,
            int nodeCount
    ) {
    }

    public record FinalizedStreamingScan(
            UUID scanId,
            UUID floorId,
            String storagePath,
            String payloadSha256,
            long fileSize,
            int nodeCount,
            int keyframeCount,
            int poiMarkCount,
            Map<String, Object> deviceInfo
    ) {
    }

    private record StreamingState(UUID floorId, Map<String, Object> deviceInfo) {
    }

    private record StoredFile(long size, String sha256) {
    }
}
