package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.http.HttpStatus;

final class ZipArchiveValidator {
    private static final long MAX_ENTRY_SIZE = 4L * 1024 * 1024 * 1024;
    private static final List<String> REQUIRED_FILES = List.of("scan_metadata.db", "rtabmap.db");

    private ZipArchiveValidator() {
    }

    static ArchiveRoot validateAndExtract(Path zipPath, Path scansRoot, UUID scanId) {
        try (ZipFile zipFile = ZipFile.builder().setPath(zipPath).get()) {
            List<ZipArchiveEntry> entries = entries(zipFile);
            ArchiveRoot archiveRoot = validateRoot(entries, scanId);
            validateRequiredFiles(entries, archiveRoot.rootName());
            validateEntries(entries);
            extractEntries(zipFile, entries, archiveRoot.rootName(), scansRoot, scanId.toString());
            return archiveRoot;
        } catch (ClientApiException e) {
            throw e;
        } catch (IOException | IllegalArgumentException e) {
            throw badRequest("ZIP_ARCHIVE_REQUIRED", "upload must be a valid zip archive");
        }
    }

    static ArchiveRoot peekRoot(Path zipPath, UUID requestedScanId) throws IOException {
        try (ZipFile zipFile = ZipFile.builder().setPath(zipPath).get()) {
            return validateRoot(entries(zipFile), requestedScanId);
        }
    }

    private static List<ZipArchiveEntry> entries(ZipFile zipFile) {
        Enumeration<ZipArchiveEntry> enumeration = zipFile.getEntries();
        return Collections.list(enumeration);
    }

    static ArchiveRoot validateRoot(List<ZipArchiveEntry> entries, UUID requestedScanId) {
        Set<String> roots = entries.stream()
                .map(ZipArchiveEntry::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.split("/", 2)[0])
                .collect(Collectors.toSet());
        if (roots.size() != 1) {
            throw badRequest("SCAN_ARCHIVE_INVALID", "zip root directory must be exactly one scan id", roots);
        }
        String rootName = roots.iterator().next();
        UUID archiveScanId;
        try {
            archiveScanId = UUID.fromString(rootName);
        } catch (IllegalArgumentException e) {
            throw badRequest("SCAN_ARCHIVE_INVALID", "zip root directory must be a UUID scan id", roots);
        }
        if (requestedScanId != null && !archiveScanId.equals(requestedScanId)) {
            throw badRequest(
                    "SCAN_ARCHIVE_INVALID",
                    "zip root directory must match scan_id",
                    Map.of("expected", requestedScanId, "actual", archiveScanId)
            );
        }
        return new ArchiveRoot(archiveScanId, rootName);
    }

    private static void validateRequiredFiles(List<ZipArchiveEntry> entries, String zipRoot) {
        Set<String> names = entries.stream().map(ZipArchiveEntry::getName).collect(Collectors.toSet());
        for (String requiredFile : REQUIRED_FILES) {
            if (!names.contains(zipRoot + "/" + requiredFile)) {
                throw badRequest("SCAN_ARCHIVE_INVALID", "missing required file: " + requiredFile);
            }
        }
    }

    private static void validateEntries(List<ZipArchiveEntry> entries) {
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

    private static void extractEntries(
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

    private static String rewriteRoot(String name, String zipRoot, String canonicalScanId) {
        if (name.equals(zipRoot)) {
            return canonicalScanId;
        }
        if (name.startsWith(zipRoot + "/")) {
            return canonicalScanId + "/" + name.substring(zipRoot.length() + 1);
        }
        return name;
    }

    private static ClientApiException badRequest(String code, String message) {
        return new ClientApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static ClientApiException badRequest(String code, String message, Object detail) {
        return new ClientApiException(HttpStatus.BAD_REQUEST, code, message, Map.of("detail", detail));
    }

    record ArchiveRoot(UUID scanId, String rootName) {
    }
}
