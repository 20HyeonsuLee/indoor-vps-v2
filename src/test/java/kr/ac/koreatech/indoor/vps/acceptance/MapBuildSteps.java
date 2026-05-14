package kr.ac.koreatech.indoor.vps.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceHttpClient;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceHttpClient.AcceptanceResponse;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceScenarioState;
import kr.ac.koreatech.indoor.vps.application.build.BuildJobRunner;

public class MapBuildSteps {
    private final AcceptanceHttpClient http;
    private final AcceptanceScenarioState state;
    private final BuildJobRunner buildJobRunner;

    public MapBuildSteps(
            AcceptanceHttpClient http,
            AcceptanceScenarioState state,
            BuildJobRunner buildJobRunner
    ) {
        this.http = http;
        this.state = state;
        this.buildJobRunner = buildJobRunner;
    }

    @When("지도 생성을 요청한다")
    public void mapBuildingIsRequested() throws Exception {
        AcceptanceResponse response = http.postJson(
                "/api/v1/floors/" + state.floorId() + "/process",
                Map.of()
        );
        state.lastResponse(response);

        if (response.statusCode() != 200) {
            return;
        }

        UUID buildJobId = UUID.fromString(response.body().path("buildJobId").asText());
        state.buildJobId(buildJobId);
        buildJobRunner.runJob(buildJobId);

        state.buildStatus(http.getJson("/api/v1/floors/" + state.floorId() + "/process/status", 200));
        loadFloorMapAndRoute();
    }

    @Given("지도 생성이 완료되어 있다")
    public void mapBuildIsCompleted() throws Exception {
        mapBuildingIsRequested();
        theMapBuildIsSucceeded();
    }

    @Then("지도 생성은 성공한다")
    @Then("빌드 상태는 성공이다")
    public void theMapBuildIsSucceeded() {
        assertThat(state.buildStatus().path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(state.buildStatus().path("progress").asDouble()).isEqualTo(1.0);
    }

    @Then("지도 생성 상태는 비어 있다")
    public void theMapBuildStatusIsEmpty() throws Exception {
        JsonNode status = http.getJson("/api/v1/floors/" + state.floorId() + "/process/status", 200);
        JsonNode buildJobId = status.path("buildJobId");
        assertThat(status.path("status").asText()).isEqualTo("IDLE");
        assertThat(buildJobId.isMissingNode() || buildJobId.isNull() || buildJobId.asText("").isBlank()).isTrue();
    }

    private void loadFloorMapAndRoute() throws Exception {
        AcceptanceResponse floorMap = http.get("/api/v1/floors/" + state.floorId() + "/map", 200);
        state.floorMapResponse(floorMap);

        if (state.startNodeId() == null || state.endNodeId() == null) {
            return;
        }
        state.route(http.getJson(
                "/api/v1/floors/" + state.floorId()
                        + "/route?from=" + state.startNodeId()
                        + "&to=" + state.endNodeId(),
                200
        ));
    }
}
