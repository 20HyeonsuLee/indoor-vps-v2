package kr.ac.koreatech.indoor.vps.application.bridge;

public enum BridgeCommand {
    HEALTH("health"),
    LOCALIZE("localize"),
    MERGE_SCAN("merge_scan"),
    BUILD_FLOOR_MAP("build_floor_map");

    private final String wireName;

    BridgeCommand(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
