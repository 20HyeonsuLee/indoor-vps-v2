package kr.ac.koreatech.indoor.vps.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceHttpClient;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceScenarioState;

public class ErrorSteps {
    private final AcceptanceHttpClient http;
    private final AcceptanceScenarioState state;

    public ErrorSteps(AcceptanceHttpClient http, AcceptanceScenarioState state) {
        this.http = http;
        this.state = state;
    }

    @When("잘못된 건물 식별자로 건물을 조회한다")
    public void aBuildingIsRequestedWithInvalidId() throws Exception {
        state.lastResponse(http.get("/api/v1/buildings/not-a-uuid", 422));
    }

    @Then("요청은 {string} 오류로 거절된다")
    public void theRequestIsRejectedWithCode(String expectedCode) {
        assertThat(state.lastResponse().body().path("code").asText()).isEqualTo(expectedCode);
    }
}
