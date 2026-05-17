package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.port;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;

public interface RtabmapGraphReader {

    RtabmapGraph read(Path dbPath, UUID scanId, UUID buildJobId);

    record RtabmapGraph(List<MapNodeEntity> nodes, List<MapEdgeEntity> edges) {
    }

    class RtabmapGraphReadException extends RuntimeException {
        public RtabmapGraphReadException(String message) {
            super(message);
        }

        public RtabmapGraphReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
