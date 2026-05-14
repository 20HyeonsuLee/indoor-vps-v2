package kr.ac.koreatech.indoor.vps.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.spring.CucumberContextConfiguration;
import io.cucumber.spring.ScenarioScope;
import java.nio.file.Path;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceHttpClient;
import kr.ac.koreatech.indoor.vps.acceptance.support.AcceptanceScenarioState;
import kr.ac.koreatech.indoor.vps.acceptance.support.FileTreeCleaner;
import kr.ac.koreatech.indoor.vps.acceptance.support.RtabmapScanFixtureFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(CucumberSpringConfiguration.AcceptanceSupportConfiguration.class)
public class CucumberSpringConfiguration {
    private static final String DB_IMAGE = System.getProperty(
            "indoor.e2e.db.image",
            "garapadev/postgres-postgis-pgvector:15-stable"
    );
    private static final Path STORAGE_ROOT = FileTreeCleaner.createTempDirectory("indoor-vps-acceptance-storage-");
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(DB_IMAGE).asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("indoor_acceptance")
            .withUsername("indoor")
            .withPassword("indoor");

    static {
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> FileTreeCleaner.deleteRecursively(STORAGE_ROOT)));
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.autoconfigure.exclude", () -> "");
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("spring.flyway.baseline-version", () -> "0");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("indoor.persistence", () -> "jpa");
        registry.add("indoor.storage-root", () -> STORAGE_ROOT.toString());
        registry.add("indoor.build-worker.enabled", () -> "false");
        registry.add("indoor.python.enabled", () -> "false");
        registry.add("indoor.python.device", () -> "cpu");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AcceptanceSupportConfiguration {
        @Bean
        AcceptanceHttpClient acceptanceHttpClient(
                ObjectMapper objectMapper,
                WebServerApplicationContext webServerContext
        ) {
            return new AcceptanceHttpClient(objectMapper, webServerContext);
        }

        @Bean
        @ScenarioScope
        AcceptanceScenarioState acceptanceScenarioState() {
            return new AcceptanceScenarioState();
        }

        @Bean
        RtabmapScanFixtureFactory rtabmapScanFixtureFactory() {
            return new RtabmapScanFixtureFactory();
        }
    }
}
