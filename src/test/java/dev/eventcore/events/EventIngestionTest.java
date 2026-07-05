package dev.eventcore.events;

import dev.eventcore.IntegrationTestBase;
import dev.eventcore.api.ApiError;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventIngestionTest extends IntegrationTestBase {

    @Autowired
    private JdbcClient jdbc;

    private record StoredEvent(String type, String payload) {}

    @Test
    void postingAnEventReturnsItsReceipt() {
        var response = postEvent("""
                {"type": "user.created", "payload": {"userId": "42", "plan": "pro"}}
                """).toEntity(EventCreated.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        EventCreated receipt = response.getBody();
        assertThat(receipt.id()).isNotNull();
        assertThat(receipt.time()).isNotNull();
        assertThat(receipt.type()).isEqualTo("user.created");
    }

    @Test
    void postedEventIsPersistedWithItsPayload() {
        EventCreated receipt = postEvent("""
                {"type": "invoice.paid", "payload": {"invoiceId": "INV-7"}}
                """).body(EventCreated.class);

        StoredEvent stored = storedEvent(receipt.id());
        assertThat(stored.type()).isEqualTo("invoice.paid");
        assertThat(stored.payload()).contains("\"invoiceId\": \"INV-7\"");
    }

    @Test
    void payloadIsOptional() {
        var response = postEvent("""
                {"type": "user.deleted"}
                """).toEntity(EventCreated.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void missingTypeIsRejected() {
        var response = postEventExpectingRejection("""
                {"payload": {"orphan": true}}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("type is required");
    }

    @Test
    void blankTypeIsRejected() {
        var response = postEventExpectingRejection("""
                {"type": "   "}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private RestClient.ResponseSpec postEvent(String body) {
        return api().post()
                .uri("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve();
    }

    private ResponseEntity<ApiError> postEventExpectingRejection(String body) {
        return postEvent(body)
                .onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(ApiError.class);
    }

    private StoredEvent storedEvent(UUID id) {
        return jdbc.sql("SELECT type, payload::text AS payload FROM events WHERE id = :id")
                .param("id", id)
                .query((row, rowNumber) -> new StoredEvent(row.getString("type"), row.getString("payload")))
                .single();
    }
}