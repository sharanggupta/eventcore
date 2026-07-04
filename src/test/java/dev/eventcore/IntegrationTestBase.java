package dev.eventcore;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class IntegrationTestBase {

    private static final DockerImageName TIMESCALE_IMAGE = DockerImageName
            .parse("timescale/timescaledb:latest-pg16")
            .asCompatibleSubstituteFor("postgres");

    // Singleton container shared across all test classes; Ryuk stops it when the JVM exits.
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(TIMESCALE_IMAGE)
            .withDatabaseName("eventcore")
            .withUsername("eventcore")
            .withPassword("eventcore");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    protected RestClient api() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }
}
