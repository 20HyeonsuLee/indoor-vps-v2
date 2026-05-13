package kr.ac.koreatech.indoor.vps.application;

import java.util.List;
import kr.ac.koreatech.indoor.vps.application.bridge.BridgeContracts.FloorMapBridgeRef;

public interface LocalizationMapProvider {
    List<FloorMapBridgeRef> activeFloorMaps(String buildingId);
}
