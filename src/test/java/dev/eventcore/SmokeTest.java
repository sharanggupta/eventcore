package dev.eventcore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeTest extends IntegrationTestBase {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void databaseAnswersQueries() {
        Integer one = jdbc.sql("SELECT 1").query(Integer.class).single();

        assertThat(one).isEqualTo(1);
    }

    @Test
    void healthEndpointSaysOk() {
        String response = api().get().uri("/health").retrieve().body(String.class);

        assertThat(response).isEqualTo("OK");
    }

    @Test
    void flywayMigrationsHaveRun() {
        String version = jdbc.sql("SELECT version FROM schema_info LIMIT 1")
                .query(String.class)
                .single();

        assertThat(version).isEqualTo("1.0.0");
    }
}
