package kr.ac.koreatech.indoor.vps.contexts.mapping.application;

import java.util.List;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.FloorMapBridgeRef;

public interface LocalizationMapProvider {
    List<FloorMapBridgeRef> activeFloorMaps(String buildingId);

    /**
     * Shadow(rtabmap-native) 변종. 각 active scan의 {@code <scan_dir>/v2/rtabmap_reprocessed.db}
     * 가 존재할 때만 그 floor를 포함. 없으면 해당 floor는 빠짐 (검증할 수 없는 floor는
     * shadow query에서 자연스럽게 제외). 기본 구현은 빈 리스트.
     */
    default List<FloorMapBridgeRef> activeFloorMapsV2(String buildingId) {
        return List.of();
    }
}
