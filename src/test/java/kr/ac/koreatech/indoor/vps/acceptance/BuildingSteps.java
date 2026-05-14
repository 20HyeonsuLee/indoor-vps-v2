package kr.ac.koreatech.indoor.vps.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Given;
import java.util.Map;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceHttpClient;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceScenarioState;

public class BuildingSteps {
    private final AcceptanceHttpClient http;
    private final AcceptanceScenarioState state;

    public BuildingSteps(AcceptanceHttpClient http, AcceptanceScenarioState state) {
        this.http = http;
        this.state = state;
    }

    @Given("건물이 존재한다")
    public void aBuildingExists() throws Exception {
        JsonNode building = http.postJson("/api/v1/buildings", Map.of(
                "name", "Acceptance Building",
                "description", "created by Cucumber acceptance test",
                "latitude", 36.764,
                "longitude", 127.282
        ), 201);
        state.buildingId(building.path("buildingId").asText());
    }

    @Given("건물에 {int}층이 존재한다")
    public void floorExistsInTheBuilding(int floorLevel) throws Exception {
        JsonNode floor = http.postJson("/api/v1/buildings/" + state.buildingId() + "/floors", Map.of(
                "name", floorLevel + "F",
                "level", floorLevel,
                "height", 3.2
        ), 201);
        state.floorId(floor.path("floorId").asText());
    }
}
