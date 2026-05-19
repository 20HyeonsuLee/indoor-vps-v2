package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port;

public enum BridgeCommand {
    HEALTH("health"),
    LOCALIZE("localize"),
    MERGE_SCAN("merge_scan"),
    BUILD_SUPERPOINT_INDEX("build_superpoint_index"),
    EXPORT_POINTCLOUD("export_pointcloud");

    private final String wireName;

    BridgeCommand(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
