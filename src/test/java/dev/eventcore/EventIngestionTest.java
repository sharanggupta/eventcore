package dev.eventcore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventIngestionTest extends IntegrationTestBase {

    @Autowired
    private JdbcClient jdbc;

    private record StoredEvent(String type, String payload) {}

    @Test
    void postingAnEventReturnsItsReceiptAndPersistsIt() {
        var response = postEvent("""
                {"type": "user.created", "payload": {"userId": "42", "plan": "pro"}}
                """).toEntity(EventCreated.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        EventCreated receipt = response.getBody();
        assertThat(receipt.id()).isNotNull();
        assertThat(receipt.time()).isNotNull();
        assertThat(receipt.type()).isEqualTo("user.created");

        StoredEvent stored = storedEvent(receipt.id());
        assertThat(stored.type()).isEqualTo("user.created");
        assertThat(stored.payload()).contains("\"userId\": \"42\"");
    }

    @Test
    void payloadIsOptional() {
        var response = postEvent("""
                {"type": "user.deleted"}
                """).toEntity(EventCreated.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void missingTypeIsRejected() {
        var response = postEvent("""
                {"payload": {"orphan": true}}
                """).onStatus(HttpStatusCode::isError, (request, res) -> { /* asserted below */ })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("type is required");
    }

    @Test
    void blankTypeIsRejected() {
        var response = postEvent("""
                {"type": "   "}
                """).onStatus(HttpStatusCode::isError, (request, res) -> { /* asserted below */ })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    private RestClient.ResponseSpec postEvent(String body) {
        return api().post()
                .uri("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve();
    }

    private StoredEvent storedEvent(UUID id) {
        return jdbc.sql("SELECT type, payload::text AS payload FROM events WHERE id = :id")
                .param("id", id)
                .query((row, rowNumber) -> new StoredEvent(row.getString("type"), row.getString("payload")))
                .single();
    }
}
