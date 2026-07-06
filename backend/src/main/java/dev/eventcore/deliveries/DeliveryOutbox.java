package dev.eventcore.deliveries;

import dev.eventcore.api.ConflictException;
import dev.eventcore.api.NotFoundException;
import dev.eventcore.events.Event;
import dev.eventcore.events.EventSink;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DeliveryOutbox implements EventSink {

    private static final int DELIVERIES_PER_POLL = 50;
    private static final String SELECT_DELIVERY =
            "SELECT id, event_id, subscription_id, status, attempts, created_at FROM webhook_deliveries";

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final DeliveryProperties properties;

    DeliveryOutbox(JdbcClient jdbc, ObjectMapper json, DeliveryProperties properties) {
        this.jdbc = jdbc;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public void enqueue(Event event) {
        for (SubscriptionTarget target : targetsFor(event.type())) {
            insertDeliveryFor(event, target);
        }
    }

    private record SubscriptionTarget(UUID id, List<String> payloadFields) {}

    private List<SubscriptionTarget> targetsFor(String eventType) {
        return jdbc.sql("""
                SELECT id, payload_fields::text AS payload_fields FROM webhook_subscriptions
                WHERE event_types IS NULL OR jsonb_exists(event_types, :type)
                """)
                .param("type", eventType)
                .query((row, rowNumber) -> new SubscriptionTarget(
                        row.getObject("id", UUID.class),
                        toFieldList(row.getString("payload_fields"))))
                .list();
    }

    private void insertDeliveryFor(Event event, SubscriptionTarget target) {
        jdbc.sql("""
                INSERT INTO webhook_deliveries (event_id, subscription_id, body, gives_up_after)
                VALUES (:eventId, :subscriptionId, CAST(:body AS jsonb), :givesUpAfter)
                """)
                .param("eventId", event.id())
                .param("subscriptionId", target.id())
                .param("body", json.writeValueAsString(deliverableViewOf(event, target.payloadFields())))
                .param("givesUpAfter", properties.maxAttempts())
                .update();
    }

    /** Payload minimization: keep only the subscription's allow-listed top-level fields. */
    private Event deliverableViewOf(Event event, List<String> payloadFields) {
        if (payloadFields == null || !(event.payload() instanceof ObjectNode payload)) {
            return event;
        }
        ObjectNode trimmed = json.createObjectNode();
        for (String field : payloadFields) {
            if (payload.has(field)) {
                trimmed.set(field, payload.get(field));
            }
        }
        return new Event(event.id(), event.time(), event.type(), trimmed);
    }

    private List<String> toFieldList(String stored) {
        return stored == null ? null : List.of(json.readValue(stored, String[].class));
    }

    List<PendingDelivery> due() {
        return jdbc.sql("""
                SELECT d.id, d.body::text AS body, d.attempts, s.url, s.secret
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

    Optional<DeliveryDetail> find(UUID id) {
        return jdbc.sql(SELECT_DELIVERY + " WHERE id = :id")
                .param("id", id)
                .query(this::toDelivery)
                .optional()
                .map(delivery -> DeliveryDetail.of(delivery, attemptsOf(id)));
    }

    RedeliveryReceipt requeue(UUID id) {
        int requeued = jdbc.sql("""
                UPDATE webhook_deliveries
                SET status = 'pending', next_attempt_at = NOW(),
                    gives_up_after = attempts + :maxAttempts
                WHERE id = :id AND status = 'failed'
                """)
                .param("id", id)
                .param("maxAttempts", properties.maxAttempts())
                .update();
        if (requeued == 0) {
            throw exists(id)
                    ? new ConflictException("only failed deliveries can be redelivered")
                    : new NotFoundException("delivery not found");
        }
        return receiptFor(id);
    }

    RedeliveredBatch requeueAllFailed(BulkRedeliveryRequest request) {
        StringBuilder sql = new StringBuilder("""
                UPDATE webhook_deliveries
                SET status = 'pending', next_attempt_at = NOW(),
                    gives_up_after = attempts + :maxAttempts
                WHERE status = 'failed'""");
        if (request.scopedToOneSubscription()) {
            sql.append(" AND subscription_id = :subscriptionId");
        }
        var statement = jdbc.sql(sql.toString()).param("maxAttempts", properties.maxAttempts());
        if (request.scopedToOneSubscription()) {
            statement = statement.param("subscriptionId", request.subscriptionId());
        }
        return new RedeliveredBatch(statement.update());
    }

    void recordSuccess(PendingDelivery delivery, AttemptOutcome outcome) {
        jdbc.sql("UPDATE webhook_deliveries SET status = 'delivered', attempts = attempts + 1 WHERE id = :id")
                .param("id", delivery.id())
                .update();
        recordAttempt(delivery, outcome);
    }

    void recordFailure(PendingDelivery delivery, AttemptOutcome outcome) {
        jdbc.sql("""
                UPDATE webhook_deliveries
                SET attempts = attempts + 1,
                    status = CASE WHEN attempts + 1 >= gives_up_after THEN 'failed' ELSE 'pending' END,
                    -- the backoff exponent restarts with each redelivery cycle
                    next_attempt_at = NOW()
                        + (:backoffMillis * POWER(2, attempts - (gives_up_after - :maxAttempts)))
                        * INTERVAL '1 millisecond'
                WHERE id = :id
                """)
                .param("id", delivery.id())
                .param("maxAttempts", properties.maxAttempts())
                .param("backoffMillis", properties.retryBackoff().toMillis())
                .update();
        recordAttempt(delivery, outcome);
    }

    private void recordAttempt(PendingDelivery delivery, AttemptOutcome outcome) {
        jdbc.sql("""
                INSERT INTO delivery_attempts
                    (delivery_id, attempt, status_code, error, response_snippet, duration_ms)
                VALUES (:deliveryId, :attempt, :statusCode, :error, :responseSnippet, :durationMs)
                """)
                .param("deliveryId", delivery.id())
                .param("attempt", delivery.attemptNumber())
                .param("statusCode", outcome.statusCode())
                .param("error", outcome.error())
                .param("responseSnippet", outcome.responseSnippet())
                .param("durationMs", outcome.durationMs())
                .update();
    }

    private List<DeliveryAttempt> attemptsOf(UUID deliveryId) {
        return jdbc.sql("""
                SELECT attempt, attempted_at, status_code, error, response_snippet, duration_ms
                FROM delivery_attempts WHERE delivery_id = :deliveryId ORDER BY attempt
                """)
                .param("deliveryId", deliveryId)
                .query(this::toAttempt)
                .list();
    }

    private RedeliveryReceipt receiptFor(UUID id) {
        return jdbc.sql("SELECT id, status, next_attempt_at FROM webhook_deliveries WHERE id = :id")
                .param("id", id)
                .query((row, rowNumber) -> new RedeliveryReceipt(
                        row.getObject("id", UUID.class),
                        row.getString("status"),
                        row.getObject("next_attempt_at", OffsetDateTime.class)))
                .single();
    }

    private boolean exists(UUID id) {
        return jdbc.sql("SELECT count(*) FROM webhook_deliveries WHERE id = :id")
                .param("id", id)
                .query(Long.class)
                .single() > 0;
    }

    private String sqlFor(DeliveryQuery query) {
        StringBuilder sql = new StringBuilder(SELECT_DELIVERY + " WHERE TRUE");
        if (query.filtersByStatus()) {
            sql.append(" AND status = :status");
        }
        if (query.boundedBelow()) {
            sql.append(" AND created_at >= :from");
        }
        if (query.boundedAbove()) {
            sql.append(" AND created_at <= :to");
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
        if (query.boundedBelow()) {
            statement = statement.param("from", query.from());
        }
        if (query.boundedAbove()) {
            statement = statement.param("to", query.to());
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

    private DeliveryAttempt toAttempt(ResultSet row, int rowNumber) throws SQLException {
        return new DeliveryAttempt(
                row.getInt("attempt"),
                row.getObject("attempted_at", OffsetDateTime.class),
                row.getObject("status_code", Integer.class),
                row.getString("error"),
                row.getString("response_snippet"),
                row.getLong("duration_ms"));
    }

    private PendingDelivery toPendingDelivery(ResultSet row, int rowNumber) throws SQLException {
        return new PendingDelivery(
                row.getObject("id", UUID.class),
                row.getString("url"),
                row.getString("body"),
                row.getString("secret"),
                row.getInt("attempts"));
    }
}