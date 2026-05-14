package kr.ac.koreatech.indoor.vps.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceHttpClient;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceHttpClient.AcceptanceResponse;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceScenarioState;

public class FloorMapSteps {
    private final AcceptanceHttpClient http;
    private final AcceptanceScenarioState state;

    public FloorMapSteps(AcceptanceHttpClient http, AcceptanceScenarioState state) {
        this.http = http;
        this.state = state;
    }

    @When("같은 지도 버전으로 층 지도를 다시 조회한다")
    public void theSameMapVersionIsRequestedAgain() throws Exception {
        String etag = state.floorMapResponse().header("etag");
        assertThat(etag).isNotBlank();

        AcceptanceResponse response = http.get(
                "/api/v1/floors/" + state.floorId() + "/map",
                Map.of("If-None-Match", etag),
                304
        );
        state.cachedMapResponse(response);
    }

    @Then("층 지도에는 연결 지점 {int}개와 연결 경로 {int}개가 있다")
    @Then("층 지도에는 노드 {int}개와 엣지 {int}개가 있다")
    public void theFloorMapHasNodesAndEdges(int expectedNodes, int expectedEdges) {
        JsonNode floorMap = state.floorMap();
        assertThat(floorMap.path("nodes").size()).isEqualTo(expectedNodes);
        assertThat(floorMap.path("edges").size()).isEqualTo(expectedEdges);
        assertThat(floorMap.path("edges").get(0).path("type").asText()).isEqualTo("rtabmap_link");
    }

    @Then("두 지점 사이의 경로 거리는 {double}미터다")
    @Then("두 스캔 노드 사이의 경로 거리는 {double}미터다")
    public void aRouteBetweenTheTwoScanNodesHasDistanceMeters(double expectedDistance) {
        JsonNode route = state.route();
        assertThat(route.path("totalDistance").asDouble()).isEqualTo(expectedDistance);
        assertThat(route.path("nodes").size()).isEqualTo(2);
        assertThat(route.path("edges").size()).isEqualTo(1);
    }

    @Then("지도 본문은 다시 내려오지 않는다")
    public void theMapBodyIsNotDownloadedAgain() {
        assertThat(state.cachedMapResponse().hasBlankBody()).isTrue();
    }
}
