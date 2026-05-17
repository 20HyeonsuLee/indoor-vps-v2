package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanArchiveStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class ScanArchiveStorageAdapter implements ScanArchiveStorage {

    private final IndoorProperties properties;

    public ScanArchiveStorageAdapter(IndoorProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredScanArchive store(UUID scanId, byte[] content, String originalFilename, boolean force) {
        if (scanId == null) {
            throw new ClientApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SCAN_ID_REQUIRED", "scan id must be resolved before storing");
        }
        if (content == null || content.length == 0) {
            throw badRequest("ZIP_ARCHIVE_REQUIRED", "scan archive zip is required");
        }

        Path storageRoot = properties.getStorageRoot().toAbsolutePath().normalize();
        Path scanRoot = storageRoot.resolve("scans").resolve(scanId.toString()).normalize();
        Path tempDir = storageRoot.resolve("tmp").resolve("scan-archives").resolve(UUID.randomUUID().toString());
        Path tempZip = tempDir.resolve("upload.zip");
        Path extractedScansRoot = tempDir.resolve("extract").resolve("scans");
        Path extractedScanRoot = extractedScansRoot.resolve(scanId.toString());

        try {
            Files.createDirectories(tempDir);
            StoredUpload storedUpload = writeAndHash(content, tempZip);
            ZipArchiveValidator.validateAndExtract(tempZip, extractedScansRoot, scanId);
            replaceScanRoot(extractedScanRoot, scanRoot, force);
            return new StoredScanArchive(
                    scanId,
                    "scans/" + scanId,
                    safeFileName(originalFilename),
                    storedUpload.size(),
                    storedUpload.sha256(),
                    scanRoot
            );
        } catch (ClientApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ClientApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SCAN_ARCHIVE_STORE_FAILED", e.getMessage());
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Override
    public UUID resolveScanId(byte[] content, String originalFilename, UUID requestedScanId) {
        if (content == null || content.length == 0) {
            throw badRequest("ZIP_ARCHIVE_REQUIRED", "scan archive zip is required");
        }
        Path storageRoot = properties.getStorageRoot().toAbsolutePath().normalize();
        Path tempDir = storageRoot.resolve("tmp").resolve("scan-archives").resolve(UUID.randomUUID().toString());
        Path tempZip = tempDir.resolve("inspect.zip");
        try {
            Files.createDirectories(tempDir);
            Files.write(tempZip, content);
            return ZipArchiveValidator.peekRoot(tempZip, requestedScanId).scanId();
        } catch (ClientApiException e) {
            throw e;
        } catch (IOException | IllegalArgumentException e) {
            throw badRequest("ZIP_ARCHIVE_REQUIRED", "upload must be a valid zip archive");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Override
    public void deleteScan(UUID scanId) {
        deleteRecursively(properties.getStorageRoot().resolve("scans").resolve(scanId.toString()));
    }

    private StoredUpload writeAndHash(byte[] content, Path destination) throws IOException {
        MessageDigest digest = sha256();
        digest.update(content);
        try (OutputStream out = Files.newOutputStream(destination)) {
            out.write(content);
        }
        return new StoredUpload(HexFormat.of().formatHex(digest.digest()), content.length);
    }

    private void replaceScanRoot(Path extractedScanRoot, Path scanRoot, boolean force) throws IOException {
        if (!Files.exists(extractedScanRoot)) {
            throw new ClientApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SCAN_ARCHIVE_STORE_FAILED", "extracted scan root is missing");
        }
        Files.createDirectories(scanRoot.getParent());
        if (!Files.exists(scanRoot)) {
            moveDirectory(extractedScanRoot, scanRoot);
            return;
        }
        if (!force) {
            throw new ClientApiException(HttpStatus.CONFLICT, "SCAN_ALREADY_EXISTS", "scan_id already exists");
        }

        Path backupRoot = scanRoot.resolveSibling(scanRoot.getFileName() + ".backup-" + UUID.randomUUID());
        moveDirectory(scanRoot, backupRoot);
        try {
            moveDirectory(extractedScanRoot, scanRoot);
            deleteRecursively(backupRoot);
        } catch (IOException | RuntimeException e) {
            if (!Files.exists(scanRoot) && Files.exists(backupRoot)) {
                moveDirectory(backupRoot, scanRoot);
            }
            throw e;
        }
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new ClientApiException(HttpStatus.INTERNAL_SERVER_ERROR, "HASH_UNAVAILABLE", e.getMessage());
        }
    }

    private String safeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload.zip";
        }
        return Path.of(originalFilename.replace('\\', '/')).getFileName().toString();
    }

    private ClientApiException badRequest(String code, String message) {
        return new ClientApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private record StoredUpload(String sha256, long size) {
    }
}
