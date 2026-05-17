package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.UUID;

public record MergedScanResult(UUID floorId, UUID activeScanId, String status) {
}
