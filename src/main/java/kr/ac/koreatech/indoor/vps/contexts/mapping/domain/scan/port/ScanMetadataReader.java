package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface ScanMetadataReader {

    Optional<ScanMetadata> read(Path metadataDbPath);

    record SessionInfo(
            String deviceModel,
            String startedAt,
            String buildingId,
            String floorId,
            String floorLevel
    ) {
    }

    record BranchMarkRow(
            long id,
            int keyframeSeq,
            String nodeType,
            double tx,
            double ty,
            double tz,
            String connectHint,
            Long connectNodeId,
            Long markSessionId
    ) {
    }

    record PoiMarkRow(
            long id,
            int keyframeSeq,
            double tx,
            double ty,
            double tz,
            String label
    ) {
    }

    record InterfloorMarkRow(
            long id,
            int keyframeSeq,
            String connectorType,
            String prefix,
            double tx,
            double ty,
            double tz
    ) {
    }

    record KeyframeRow(
            int seq,
            Integer rtabmapNodeId,
            double tx,
            double ty,
            double tz
    ) {
    }

    record ScanMetadata(
            SessionInfo session,
            List<KeyframeRow> keyframes,
            List<BranchMarkRow> branchMarks,
            List<PoiMarkRow> poiMarks,
            List<InterfloorMarkRow> interfloorMarks
    ) {
    }

    class ScanMetadataReadException extends RuntimeException {
        public ScanMetadataReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
