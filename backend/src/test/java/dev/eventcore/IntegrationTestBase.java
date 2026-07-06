package dev.eventcore;

import dev.eventcore.security.ApiKeyStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    private static final DockerImageName TIMESCALE_IMAGE = DockerImageName
            .parse("timescale/timescaledb:2.27.0-pg16")
            .asCompatibleSubstituteFor("postgres");

    // Singleton container shared across all test classes; Ryuk stops it when the JVM exits.
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(TIMESCALE_IMAGE)
            .withDatabaseName("eventcore")
            .withUsername("eventcore")
            .withPassword("eventcore");

    static {
        postgres.start();
    }

    protected static final String ADMIN_TOKEN = "test-admin-token";

    // One key for the whole test run; the database outlives individual Spring contexts.
    private static String sharedApiKey;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("eventcore.security.admin-token", () -> ADMIN_TOKEN);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ApiKeyStore apiKeys;

    @Autowired
    private JdbcClient jdbc;

    /** Deletes in foreign-key-safe order; keeps api_keys so the shared test key stays valid. */
    protected void wipeAllData() {
        jdbc.sql("DELETE FROM pull_subscriptions").update();
        jdbc.sql("DELETE FROM webhook_deliveries").update();
        jdbc.sql("DELETE FROM webhook_subscriptions").update();
        jdbc.sql("DELETE FROM events").update();
    }

    protected RestClient api() {
        return RestClient.builder()
                .baseUrl(baseUrl())
                .defaultHeader("X-API-Key", testApiKey())
                .build();
    }

    protected RestClient anonymousApi() {
        return RestClient.builder()
                .baseUrl(baseUrl())
                .build();
    }

    protected int serverPort() {
        return port;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String testApiKey() {
        if (sharedApiKey == null) {
            sharedApiKey = apiKeys.issue("integration-tests").key();
        }
        return sharedApiKey;
    }
}