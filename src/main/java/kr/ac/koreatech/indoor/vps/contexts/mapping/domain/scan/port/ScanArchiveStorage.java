package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port;

import java.nio.file.Path;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ScanArchiveStorage {

    UUID resolveScanId(MultipartFile upload, UUID requestedScanId);

    StoredScanArchive store(UUID scanId, MultipartFile upload, boolean force);

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
