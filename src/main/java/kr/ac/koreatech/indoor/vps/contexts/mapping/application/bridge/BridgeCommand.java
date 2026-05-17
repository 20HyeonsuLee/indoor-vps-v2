package kr.ac.koreatech.indoor.vps.contexts.mapping.application.bridge;

public enum BridgeCommand {
    HEALTH("health"),
    LOCALIZE("localize"),
    MERGE_SCAN("merge_scan");

    private final String wireName;

    BridgeCommand(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
