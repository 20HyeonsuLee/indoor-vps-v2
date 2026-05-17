package kr.ac.koreatech.indoor.vps.karate.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.karate.support.RtabmapScanFixtureFactory.ScanFixture;
import kr.ac.koreatech.indoor.vps.karate.support.StreamingScanFixtureFactory.StreamingFixture;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.build.BuildJobRunner;

public final class AcceptanceKarateSupport {
    private static BuildJobRunner buildJobRunner;
    private static Path storageRoot;

    private AcceptanceKarateSupport() {
    }

    public static void configure(BuildJobRunner runner, Path root) {
        buildJobRunner = runner;
        storageRoot = root.toAbsolutePath().normalize();
    }

    public static Map<String, Object> twoConnectedNodesScan() throws Exception {
        ScanFixture fixture = new RtabmapScanFixtureFactory().twoConnectedNodes();
        Path zip = Files.createTempFile("indoor-karate-scan-", ".zip");
        Files.write(zip, fixture.zipBytes());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanId", fixture.scanId().toString());
        result.put("startNodeId", fixture.startNodeId().toString());
        result.put("endNodeId", fixture.endNodeId().toString());
        result.put("zipReadPath", fileReadPath(zip));
        return result;
    }

    public static Map<String, Object> twoLinkedFramesStreamingScan() throws Exception {
        StreamingFixture fixture = new StreamingScanFixtureFactory().twoLinkedFrames();
        Path manifest = writeTempFile("indoor-karate-manifest-", ".json", fixture.manifestBytes());
        Path metadata = writeTempFile("indoor-karate-metadata-", ".db", fixture.metadataBytes());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanId", fixture.scanId().toString());
        result.put("startBody", fixture.startBody());
        result.put("framesBody", fixture.framesBody());
        result.put("manifestReadPath", fileReadPath(manifest));
        result.put("metadataReadPath", fileReadPath(metadata));
        return result;
    }

    public static void runBuildJob(String buildJobId) {
        buildJobRunner.runJob(UUID.fromString(buildJobId));
    }

    public static boolean scanFileExists(String scanId, String fileName) {
        return Files.exists(scanRoot(scanId).resolve(fileName));
    }

    public static boolean rtabmapTableExists(String scanId, String tableName) throws SQLException {
        try (Connection connection = sqliteConnection(scanId);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '" + tableName + "'"
             )) {
            return result.next();
        }
    }

    public static int rtabmapRowCount(String scanId, String tableName) throws SQLException {
        try (Connection connection = sqliteConnection(scanId);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static Path writeTempFile(String prefix, String suffix, byte[] bytes) throws IOException {
        Path path = Files.createTempFile(prefix, suffix);
        Files.write(path, bytes);
        return path;
    }

    private static String fileReadPath(Path path) {
        return "file:" + path.toAbsolutePath().normalize();
    }

    private static Connection sqliteConnection(String scanId) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + scanRoot(scanId).resolve("rtabmap.db"));
    }

    private static Path scanRoot(String scanId) {
        if (storageRoot == null) {
            throw new IllegalStateException("AcceptanceKarateSupport is not configured");
        }
        return storageRoot.resolve("scans").resolve(scanId);
    }
}
