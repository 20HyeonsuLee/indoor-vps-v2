package kr.ac.koreatech.indoor.vps.contexts.mapping.application;

import java.util.List;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.FloorMapBridgeRef;

public interface LocalizationMapProvider {
    List<FloorMapBridgeRef> activeFloorMaps(String buildingId);
}
