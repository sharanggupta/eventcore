package dev.eventcore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @Test
    void applicationStarts() {
        assertThat(postgres.isRunning()).isTrue();
    }

    @Test
    void databaseConnectivityWorks() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(5)).isTrue();
        }
    }

    @Test
    void healthEndpointReturns200() {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        String response = restClient.get()
                .uri("/health")
                .retrieve()
                .body(String.class);

        assertThat(response).isEqualTo("OK");
    }

    @Test
    void flywayMigrationsExecuted() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            var statement = connection.createStatement();
            var resultSet = statement.executeQuery("SELECT version FROM schema_info LIMIT 1");
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("version")).isEqualTo("1.0.0");
        }
    }
}
