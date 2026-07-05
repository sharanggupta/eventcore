package dev.eventcore.webhooks;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
class WebhookStore {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    WebhookStore(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    RegisteredWebhook register(String url, List<String> eventTypes, List<String> payloadFields) {
        RegisteredWebhook webhook = RegisteredWebhook.now(url, eventTypes, payloadFields);
        jdbc.sql("INSERT INTO webhook_subscriptions (id, created_at, url, secret, event_types, payload_fields) "
                        + "VALUES (:id, :createdAt, :url, :secret, CAST(:eventTypes AS jsonb), CAST(:payloadFields AS jsonb))")
                .param("id", webhook.id())
                .param("createdAt", webhook.createdAt())
                .param("url", webhook.url())
                .param("secret", webhook.secret())
                .param("eventTypes", asJson(webhook.eventTypes()), Types.VARCHAR)
                .param("payloadFields", asJson(webhook.payloadFields()), Types.VARCHAR)
                .update();
        return webhook;
    }

    boolean updateFilters(UUID id, List<String> eventTypes, List<String> payloadFields) {
        int updated = jdbc.sql("UPDATE webhook_subscriptions "
                        + "SET event_types = CAST(:eventTypes AS jsonb), "
                        + "payload_fields = CAST(:payloadFields AS jsonb) WHERE id = :id")
                .param("id", id)
                .param("eventTypes", asJson(eventTypes), Types.VARCHAR)
                .param("payloadFields", asJson(payloadFields), Types.VARCHAR)
                .update();
        return updated > 0;
    }

    boolean remove(UUID id) {
        int removed = jdbc.sql("DELETE FROM webhook_subscriptions WHERE id = :id")
                .param("id", id)
                .update();
        return removed > 0;
    }

    List<WebhookSubscription> all() {
        return jdbc.sql("SELECT id, created_at, url, event_types::text AS event_types, "
                        + "payload_fields::text AS payload_fields "
                        + "FROM webhook_subscriptions ORDER BY created_at")
                .query(this::toSubscription)
                .list();
    }

    WebhookSubscription one(UUID id) {
        return jdbc.sql("SELECT id, created_at, url, event_types::text AS event_types, "
                        + "payload_fields::text AS payload_fields "
                        + "FROM webhook_subscriptions WHERE id = :id")
                .param("id", id)
                .query(this::toSubscription)
                .single();
    }

    private WebhookSubscription toSubscription(ResultSet row, int rowNumber) throws SQLException {
        return new WebhookSubscription(
                row.getObject("id", UUID.class),
                row.getObject("created_at", OffsetDateTime.class),
                row.getString("url"),
                asEventTypes(row.getString("event_types")),
                asEventTypes(row.getString("payload_fields")));
    }

    private String asJson(List<String> eventTypes) {
        return eventTypes == null ? null : json.writeValueAsString(eventTypes);
    }

    private List<String> asEventTypes(String stored) {
        return stored == null ? null : List.of(json.readValue(stored, String[].class));
    }
}