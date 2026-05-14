package kr.ac.koreatech.indoor.vps.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceHttpClient;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceScenarioState;

public class PathfindingSteps {
    private final AcceptanceHttpClient http;
    private final AcceptanceScenarioState state;

    public PathfindingSteps(AcceptanceHttpClient http, AcceptanceScenarioState state) {
        this.http = http;
        this.state = state;
    }

    @When("존재하지 않는 목적지로 길찾기를 요청한다")
    public void pathfindingIsRequestedWithUnknownDestination() throws Exception {
        JsonNode pathfinding = http.postJson("/api/v1/buildings/" + state.buildingId() + "/pathfinding", Map.of(
                "startFloorLevel", 1,
                "startX", 0.0,
                "startY", 0.0,
                "startZ", 0.0,
                "destinationName", "없는 목적지"
        ), 200);
        state.pathfinding(pathfinding);
    }

    @Then("목적지는 찾지 못한 상태로 응답된다")
    public void theDestinationIsMarkedAsNotFound() {
        JsonNode response = state.pathfinding();
        assertThat(response.path("routeMetadata").path("destinationFound").asBoolean()).isFalse();
        assertThat(response.path("steps").size()).isEqualTo(1);
        assertThat(response.path("steps").get(0).path("instruction").asText()).isEqualTo("Start");
    }

    @Then("이동 거리는 {double}미터다")
    public void thePathfindingDistanceIsMeters(double expectedDistance) {
        assertThat(state.pathfinding().path("totalDistance").asDouble()).isEqualTo(expectedDistance);
    }
}
