package dev.eventcore;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
class DeliveryOutbox {

    private static final int DELIVERIES_PER_POLL = 50;

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final WebhookProperties webhooks;

    DeliveryOutbox(JdbcClient jdbc, ObjectMapper json, WebhookProperties webhooks) {
        this.jdbc = jdbc;
        this.json = json;
        this.webhooks = webhooks;
    }

    void enqueue(Event event) {
        jdbc.sql("""
                INSERT INTO webhook_deliveries (event_id, subscription_id, body)
                SELECT :eventId, id, CAST(:body AS jsonb) FROM webhook_subscriptions
                """)
                .param("eventId", event.id())
                .param("body", json.writeValueAsString(event))
                .update();
    }

    List<PendingDelivery> due() {
        return jdbc.sql("""
                SELECT d.id, d.body::text AS body, s.url, s.secret
                FROM webhook_deliveries d
                JOIN webhook_subscriptions s ON s.id = d.subscription_id
                WHERE d.status = 'pending' AND d.next_attempt_at <= NOW()
                ORDER BY d.next_attempt_at
                LIMIT :batch
                """)
                .param("batch", DELIVERIES_PER_POLL)
                .query(this::toPendingDelivery)
                .list();
    }

    DeliveryPage page(DeliveryQuery query) {
        List<Delivery> fetched = bindConditions(jdbc.sql(sqlFor(query)), query)
                .param("limit", query.rowsToFetch())
                .query(this::toDelivery)
                .list();
        return DeliveryPage.from(fetched, query.limit());
    }

    private String sqlFor(DeliveryQuery query) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, event_id, subscription_id, status, attempts, created_at "
                        + "FROM webhook_deliveries WHERE TRUE");
        if (query.filtersByStatus()) {
            sql.append(" AND status = :status");
        }
        if (!query.startsFromTheTop()) {
            sql.append(" AND (created_at, id) < (:createdAt, :id)");
        }
        return sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit").toString();
    }

    private JdbcClient.StatementSpec bindConditions(JdbcClient.StatementSpec statement, DeliveryQuery query) {
        if (query.filtersByStatus()) {
            statement = statement.param("status", query.status());
        }
        if (!query.startsFromTheTop()) {
            statement = statement.param("createdAt", query.after().time()).param("id", query.after().id());
        }
        return statement;
    }

    private Delivery toDelivery(ResultSet row, int rowNumber) throws SQLException {
        return new Delivery(
                row.getObject("id", UUID.class),
                row.getObject("event_id", UUID.class),
                row.getObject("subscription_id", UUID.class),
                row.getString("status"),
                row.getInt("attempts"),
                row.getObject("created_at", OffsetDateTime.class));
    }

    void recordSuccess(PendingDelivery delivery) {
        jdbc.sql("UPDATE webhook_deliveries SET status = 'delivered', attempts = attempts + 1 WHERE id = :id")
                .param("id", delivery.id())
                .update();
    }

    void recordFailure(PendingDelivery delivery) {
        jdbc.sql("""
                UPDATE webhook_deliveries
                SET attempts = attempts + 1,
                    status = CASE WHEN attempts + 1 >= :maxAttempts THEN 'failed' ELSE 'pending' END,
                    next_attempt_at = NOW() + (:backoffMillis * POWER(2, attempts)) * INTERVAL '1 millisecond'
                WHERE id = :id
                """)
                .param("id", delivery.id())
                .param("maxAttempts", webhooks.maxAttempts())
                .param("backoffMillis", webhooks.retryBackoff().toMillis())
                .update();
    }

    private PendingDelivery toPendingDelivery(ResultSet row, int rowNumber) throws SQLException {
        return new PendingDelivery(
                row.getObject("id", UUID.class),
                row.getString("url"),
                row.getString("body"),
                row.getString("secret"));
    }
}
