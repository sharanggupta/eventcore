package dev.eventcore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventIngestionTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void postEventReturns201WithIdAndPersistsRow() throws SQLException {
        var response = client().post()
                .uri("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"type": "user.created", "payload": {"userId": "42", "plan": "pro"}}
                        """)
                .retrieve()
                .toEntity(Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).containsKey("id").containsKey("time");
        assertThat(response.getBody().get("type")).isEqualTo("user.created");

        String id = (String) response.getBody().get("id");
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT type, payload::text AS payload FROM events WHERE id = ?::uuid")) {
            statement.setString(1, id);
            var resultSet = statement.executeQuery();
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("type")).isEqualTo("user.created");
            assertThat(resultSet.getString("payload")).contains("\"userId\": \"42\"");
        }
    }

    @Test
    void postEventWithoutPayloadIsAccepted() {
        var response = client().post()
                .uri("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"type": "user.deleted"}
                        """)
                .retrieve()
                .toEntity(Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void postEventWithMissingTypeReturns400() {
        var response = client().post()
                .uri("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"payload": {"orphan": true}}
                        """)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { /* assert below instead */ })
                .toEntity(Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("error", "type is required");
    }

    @Test
    void postEventWithBlankTypeReturns400() {
        var response = client().post()
                .uri("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"type": "   "}
                        """)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { /* assert below instead */ })
                .toEntity(Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}
