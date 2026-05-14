package kr.ac.koreatech.indoor.vps.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceHttpClient;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceHttpClient.AcceptanceResponse;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceScenarioState;
import kr.ac.koreatech.indoor.vps.acceptance.support.RtabmapScanFixtureFactory;
import kr.ac.koreatech.indoor.vps.acceptance.support.RtabmapScanFixtureFactory.ScanFixture;

public class ScanSteps {
    private final AcceptanceHttpClient http;
    private final AcceptanceScenarioState state;
    private final RtabmapScanFixtureFactory fixtures;

    public ScanSteps(
            AcceptanceHttpClient http,
            AcceptanceScenarioState state,
            RtabmapScanFixtureFactory fixtures
    ) {
        this.http = http;
        this.state = state;
        this.fixtures = fixtures;
    }

    @Given("두 지점이 연결된 스캔 파일이 준비되어 있다")
    public void aScanFileHasTwoConnectedPoints() throws Exception {
        ScanFixture scanFixture = fixtures.twoConnectedNodes();
        state.scanFixture(scanFixture);
        state.scanId(scanFixture.scanId());
        state.startNodeId(scanFixture.startNodeId());
        state.endNodeId(scanFixture.endNodeId());
    }

    @Given("층에 스캔 파일을 업로드했다")
    @When("층에 스캔 파일을 업로드한다")
    public void theScanFileIsUploadedToTheFloor() throws Exception {
        JsonNode upload = uploadScan(201).body();
        assertActiveUpload(upload);
    }

    @When("같은 스캔 파일을 다시 업로드한다")
    public void theSameScanFileIsUploadedAgain() throws Exception {
        state.lastResponse(uploadScan(409));
    }

    @When("같은 스캔 파일을 교체 옵션으로 업로드한다")
    public void theSameScanFileIsUploadedWithReplaceOption() throws Exception {
        AcceptanceResponse response = uploadScan(Map.of("force", "true"), 201);
        state.lastResponse(response);
    }

    @Then("스캔 파일은 활성 상태로 등록된다")
    public void theScanFileIsActive() {
        assertActiveUpload(state.lastResponse().body());
    }

    private AcceptanceResponse uploadScan(int expectedStatus) throws Exception {
        return uploadScan(Map.of(), expectedStatus);
    }

    private AcceptanceResponse uploadScan(Map<String, String> queryParams, int expectedStatus) throws Exception {
        AcceptanceResponse response = http.postMultipartResponse(
                "/api/v1/floors/" + state.floorId() + "/scans/chunks",
                queryParams,
                "file",
                "scan.zip",
                "application/zip",
                state.scanFixture().zipBytes(),
                expectedStatus
        );
        state.lastResponse(response);
        return response;
    }

    private void assertActiveUpload(JsonNode upload) {
        assertThat(upload.path("scanId").asText()).isEqualTo(state.scanId().toString());
        assertThat(upload.path("active").asBoolean()).isTrue();
    }
}
