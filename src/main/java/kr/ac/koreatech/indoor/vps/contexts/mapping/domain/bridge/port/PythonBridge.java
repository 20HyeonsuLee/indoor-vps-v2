package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port;

import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.BuildSuperpointIndexRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.BuildSuperpointIndexResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.ExportPointcloudRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.ExportPointcloudResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.HealthBridgeResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.LocalizeBridgeRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.LocalizeBridgeResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.MergeScanBridgeRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.MergeScanBridgeResponse;

public interface PythonBridge {
    void ensureEnabled();

    HealthBridgeResponse health();

    LocalizeBridgeResponse localize(LocalizeBridgeRequest request);

    MergeScanBridgeResponse mergeScan(MergeScanBridgeRequest request);

    BuildSuperpointIndexResponse buildSuperpointIndex(BuildSuperpointIndexRequest request);

    ExportPointcloudResponse exportPointcloud(ExportPointcloudRequest request);
}
