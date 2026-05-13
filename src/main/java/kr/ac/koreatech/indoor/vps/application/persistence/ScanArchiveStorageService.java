package kr.ac.koreatech.indoor.vps.application.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kr.ac.koreatech.indoor.vps.api.ClientApiException;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class ScanArchiveStorageService {
    private static final long MAX_ENTRY_SIZE = 4L * 1024 * 1024 * 1024;
    private static final List<String> REQUIRED_FILES = List.of("scan_metadata.db", "rtabmap.db");

    private final IndoorProperties properties;

    public ScanArchiveStorageService(IndoorProperties properties) {
        this.properties = properties;
    }

    public StoredScanArchive store(UUID scanId, MultipartFile upload, boolean force) {
        if (upload == null || upload.isEmpty()) {
            throw badRequest("ZIP_ARCHIVE_REQUIRED", "scan archive zip is required");
        }

        Path storageRoot = properties.getStorageRoot().toAbsolutePath().normalize();
        Path scanRoot = storageRoot.resolve("scans").resolve(scanId.toString()).normalize();
        Path tempDir = storageRoot.resolve("tmp").resolve("scan-archives").resolve(UUID.randomUUID().toString());
        Path tempZip = tempDir.resolve("upload.zip");

        try {
            Files.createDirectories(tempDir);
            StoredUpload storedUpload = copyAndHash(upload, tempZip);
            if (force) {
                deleteRecursively(scanRoot);
            }
            extractArchive(tempZip, storageRoot.resolve("scans"), scanId);
            return new StoredScanArchive(
                    scanId,
                    "scans/" + scanId,
                    safeFileName(upload.getOriginalFilename()),
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

    public void deleteScan(UUID scanId) {
        deleteRecursively(properties.getStorageRoot().resolve("scans").resolve(scanId.toString()));
    }

    private StoredUpload copyAndHash(MultipartFile upload, Path destination) throws IOException {
        MessageDigest digest = sha256();
        long size = 0;
        try (InputStream in = upload.getInputStream(); OutputStream out = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
                size += read;
            }
        }
        return new StoredUpload(HexFormat.of().formatHex(digest.digest()), size);
    }

    private void extractArchive(Path zipPath, Path scansRoot, UUID scanId) {
        try (ZipFile zipFile = ZipFile.builder().setPath(zipPath).get()) {
            List<ZipArchiveEntry> entries = entries(zipFile);
            String zipRoot = validateRoot(entries, scanId);
            validateRequiredFiles(entries, zipRoot);
            validateEntries(entries);
            extractEntries(zipFile, entries, zipRoot, scansRoot, scanId.toString());
        } catch (ClientApiException e) {
            throw e;
        } catch (IOException | IllegalArgumentException e) {
            throw badRequest("ZIP_ARCHIVE_REQUIRED", "upload must be a valid zip archive");
        }
    }

    private List<ZipArchiveEntry> entries(ZipFile zipFile) {
        Enumeration<ZipArchiveEntry> enumeration = zipFile.getEntries();
        return Collections.list(enumeration);
    }

    private String validateRoot(List<ZipArchiveEntry> entries, UUID scanId) {
        Set<String> roots = entries.stream()
                .map(ZipArchiveEntry::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.split("/", 2)[0])
                .collect(Collectors.toSet());
        Set<String> lowerRoots = roots.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        String expected = scanId.toString().toLowerCase();
        if (!lowerRoots.equals(Set.of(expected))) {
            throw badRequest(
                    "SCAN_ARCHIVE_INVALID",
                    "zip root directory must be exactly the scan id",
                    roots
            );
        }
        return roots.iterator().next();
    }

    private void validateRequiredFiles(List<ZipArchiveEntry> entries, String zipRoot) {
        Set<String> names = entries.stream()
                .map(ZipArchiveEntry::getName)
                .collect(Collectors.toSet());
        for (String requiredFile : REQUIRED_FILES) {
            String expected = zipRoot + "/" + requiredFile;
            if (!names.contains(expected)) {
                throw badRequest("SCAN_ARCHIVE_INVALID", "missing required file: " + requiredFile);
            }
        }
    }

    private void validateEntries(List<ZipArchiveEntry> entries) {
        for (ZipArchiveEntry entry : entries) {
            if (entry.isUnixSymlink()) {
                throw badRequest("SCAN_ARCHIVE_INVALID", "zip symlink entries are not allowed");
            }
            if (entry.getSize() > MAX_ENTRY_SIZE) {
                throw badRequest("SCAN_ARCHIVE_INVALID", "zip entry is too large: " + entry.getName());
            }
            Path normalized = Path.of(entry.getName()).normalize();
            if (normalized.isAbsolute() || normalized.startsWith("..")) {
                throw badRequest("SCAN_ARCHIVE_INVALID", "zip entry escapes scan root: " + entry.getName());
            }
        }
    }

    private void extractEntries(
            ZipFile zipFile,
            List<ZipArchiveEntry> entries,
            String zipRoot,
            Path scansRoot,
            String canonicalScanId
    ) throws IOException {
        Path safeBase = scansRoot.resolve(canonicalScanId).toAbsolutePath().normalize();
        for (ZipArchiveEntry entry : entries) {
            String rewrittenName = rewriteRoot(entry.getName(), zipRoot, canonicalScanId);
            Path target = scansRoot.resolve(rewrittenName).toAbsolutePath().normalize();
            if (!target.equals(safeBase) && !target.startsWith(safeBase)) {
                throw badRequest("SCAN_ARCHIVE_INVALID", "zip entry escapes scan root: " + entry.getName());
            }
            if (entry.isDirectory()) {
                Files.createDirectories(target);
                continue;
            }
            Files.createDirectories(target.getParent());
            try (InputStream in = zipFile.getInputStream(entry); OutputStream out = Files.newOutputStream(target)) {
                in.transferTo(out);
            }
        }
    }

    private String rewriteRoot(String name, String zipRoot, String canonicalScanId) {
        if (name.equals(zipRoot)) {
            return canonicalScanId;
        }
        if (name.startsWith(zipRoot + "/")) {
            return canonicalScanId + "/" + name.substring(zipRoot.length() + 1);
        }
        return name;
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

    private ClientApiException badRequest(String code, String message, Object detail) {
        return new ClientApiException(HttpStatus.BAD_REQUEST, code, message, java.util.Map.of("detail", detail));
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

    public record StoredScanArchive(
            UUID scanId,
            String storagePath,
            String fileName,
            long size,
            String sha256,
            Path scanRoot
    ) {
    }

    private record StoredUpload(String sha256, long size) {
    }
}
