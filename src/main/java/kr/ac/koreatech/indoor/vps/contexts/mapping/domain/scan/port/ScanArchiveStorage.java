package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port;

import java.nio.file.Path;
import java.util.UUID;

public interface ScanArchiveStorage {

    UUID resolveScanId(byte[] content, String originalFilename, UUID requestedScanId);

    StoredScanArchive store(UUID scanId, byte[] content, String originalFilename, boolean force);

    void deleteScan(UUID scanId);

    record StoredScanArchive(
            UUID scanId,
            String storagePath,
            String fileName,
            long size,
            String sha256,
            Path scanRoot
    ) {
    }
}
