package kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaEntity;

/**
 * 수동 편집(노드/엣지/POI 추가) 시 SSOT scan/build_job에 piggy-back 하기 위한 컨텍스트.
 * area의 active FloorScan → ScanIngest → buildJobId 체인을 한 곳에서 해결.
 */
public record ManualEditScope(
        FloorAreaEntity area,
        UUID scanId,
        UUID buildJobId
) {
}
