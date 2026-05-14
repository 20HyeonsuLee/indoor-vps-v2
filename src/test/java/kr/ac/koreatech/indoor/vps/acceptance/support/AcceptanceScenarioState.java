package kr.ac.koreatech.indoor.vps.acceptance.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceHttpClient.AcceptanceResponse;
import kr.ac.koreatech.indoor.vps.acceptance.support.RtabmapScanFixtureFactory.ScanFixture;

public class AcceptanceScenarioState {
    private String buildingId;
    private String floorId;
    private UUID scanId;
    private UUID buildJobId;
    private UUID startNodeId;
    private UUID endNodeId;
    private JsonNode buildStatus;
    private JsonNode floorMap;
    private JsonNode route;
    private JsonNode pathfinding;
    private ScanFixture scanFixture;
    private AcceptanceResponse lastResponse;
    private AcceptanceResponse floorMapResponse;
    private AcceptanceResponse cachedMapResponse;

    public String buildingId() {
        return buildingId;
    }

    public void buildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    public String floorId() {
        return floorId;
    }

    public void floorId(String floorId) {
        this.floorId = floorId;
    }

    public UUID scanId() {
        return scanId;
    }

    public void scanId(UUID scanId) {
        this.scanId = scanId;
    }

    public UUID buildJobId() {
        return buildJobId;
    }

    public void buildJobId(UUID buildJobId) {
        this.buildJobId = buildJobId;
    }

    public UUID startNodeId() {
        return startNodeId;
    }

    public void startNodeId(UUID startNodeId) {
        this.startNodeId = startNodeId;
    }

    public UUID endNodeId() {
        return endNodeId;
    }

    public void endNodeId(UUID endNodeId) {
        this.endNodeId = endNodeId;
    }

    public JsonNode buildStatus() {
        return buildStatus;
    }

    public void buildStatus(JsonNode buildStatus) {
        this.buildStatus = buildStatus;
    }

    public JsonNode floorMap() {
        return floorMap;
    }

    public void floorMap(JsonNode floorMap) {
        this.floorMap = floorMap;
    }

    public JsonNode route() {
        return route;
    }

    public void route(JsonNode route) {
        this.route = route;
    }

    public JsonNode pathfinding() {
        return pathfinding;
    }

    public void pathfinding(JsonNode pathfinding) {
        this.pathfinding = pathfinding;
    }

    public ScanFixture scanFixture() {
        return scanFixture;
    }

    public void scanFixture(ScanFixture scanFixture) {
        this.scanFixture = scanFixture;
    }

    public AcceptanceResponse lastResponse() {
        return lastResponse;
    }

    public void lastResponse(AcceptanceResponse lastResponse) {
        this.lastResponse = lastResponse;
    }

    public AcceptanceResponse floorMapResponse() {
        return floorMapResponse;
    }

    public void floorMapResponse(AcceptanceResponse floorMapResponse) {
        this.floorMapResponse = floorMapResponse;
        this.floorMap = floorMapResponse.body();
    }

    public AcceptanceResponse cachedMapResponse() {
        return cachedMapResponse;
    }

    public void cachedMapResponse(AcceptanceResponse cachedMapResponse) {
        this.cachedMapResponse = cachedMapResponse;
    }
}
