package kr.ac.koreatech.indoor.vps.karate;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import kr.ac.koreatech.indoor.vps.app.IndoorVpsV2Application;
import kr.ac.koreatech.indoor.vps.karate.support.AcceptanceKarateSupport;
import kr.ac.koreatech.indoor.vps.karate.support.FileTreeCleaner;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.build.BuildJobRunner;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("e2e")
@SpringBootTest(classes = IndoorVpsV2Application.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class AcceptanceKarateTest {
    private static final String DB_IMAGE = System.getProperty(
            "indoor.e2e.db.image",
            "garapadev/postgres-postgis-pgvector:15-stable"
    );
    private static final Path STORAGE_ROOT = FileTreeCleaner.createTempDirectory("indoor-vps-karate-storage-");
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

    @Autowired
    WebServerApplicationContext webServerContext;

    @Autowired
    BuildJobRunner buildJobRunner;

    @Autowired
    IndoorProperties indoorProperties;

    @Test
    void acceptanceScenarios() {
        String baseUrl = "http://127.0.0.1:" + webServerContext.getWebServer().getPort();
        System.setProperty("karate.baseUrl", baseUrl);
        AcceptanceKarateSupport.configure(buildJobRunner, indoorProperties.getStorageRoot());

        SuiteResult results = Runner.path("classpath:karate/acceptance")
                .tags("@acceptance")
                .parallel(1);

        assertFalse(results.isFailed(), results.toString());
    }
}
