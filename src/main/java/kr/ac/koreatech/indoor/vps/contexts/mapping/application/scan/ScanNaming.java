package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;

final class ScanNaming {
    private ScanNaming() {
    }

    static String publicScanFileName(FloorScanEntity scan) {
        String fileName = scan.getFileName();
        if (fileName == null || fileName.isBlank() || "rtabmap.db".equals(fileName)) {
            return streamingScanFileName(scan.getScan().getScanId());
        }
        return fileName;
    }

    static String streamingScanFileName(UUID scanId) {
        return scanId + ".db";
    }
}
