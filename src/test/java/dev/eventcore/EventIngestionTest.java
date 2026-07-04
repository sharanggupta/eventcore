package dev.eventcore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class EventIngestionTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @Test
    void postEventReturns201WithIdAndPersistsRow() throws SQLException {
        var response = postEvent("""
                {"type": "user.created", "payload": {"userId": "42", "plan": "pro"}}
                """).toEntity(EventCreated.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        EventCreated event = response.getBody();
        assertThat(event.id()).isNotNull();
        assertThat(event.time()).isNotNull();
        assertThat(event.type()).isEqualTo("user.created");

        assertEventPersisted(event);
    }

    @Test
    void postEventWithoutPayloadIsAccepted() {
        var response = postEvent("""
                {"type": "user.deleted"}
                """).toEntity(EventCreated.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void postEventWithMissingTypeReturns400() {
        var response = postEvent("""
                {"payload": {"orphan": true}}
                """).onStatus(HttpStatusCode::isError, (req, res) -> { /* assert below instead */ })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("type is required");
    }

    @Test
    void postEventWithBlankTypeReturns400() {
        var response = postEvent("""
                {"type": "   "}
                """).onStatus(HttpStatusCode::isError, (req, res) -> { /* assert below instead */ })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    private RestClient.ResponseSpec postEvent(String body) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build()
                .post()
                .uri("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve();
    }

    private void assertEventPersisted(EventCreated event) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT type, payload::text AS payload FROM events WHERE id = ?::uuid")) {
            statement.setString(1, event.id().toString());
            var resultSet = statement.executeQuery();
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("type")).isEqualTo("user.created");
            assertThat(resultSet.getString("payload")).contains("\"userId\": \"42\"");
        }
    }
}
