package dev.eventcore.metrics;

import dev.eventcore.IntegrationTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsTest extends IntegrationTestBase {

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void startWithAnEmptyPipeline() {
        wipeAllData();
    }

    @Test
    void metricsAreServedAsPrometheusTextWithoutAnApiKey() {
        String metrics = scrapeMetrics();

        assertThat(metrics).contains("# TYPE eventcore_deliveries gauge");
        assertThat(metrics).contains("# TYPE eventcore_events_ingested_total gauge");
    }

    @Test
    void deliveryCountsAreReportedPerStatus() {
        UUID subscription = insertSubscription();
        insertDelivery(subscription, "failed", OffsetDateTime.now());
        insertDelivery(subscription, "failed", OffsetDateTime.now());
        insertDelivery(subscription, "delivered", OffsetDateTime.now());

        String metrics = scrapeMetrics();

        assertThat(metrics).contains("eventcore_deliveries{status=\"failed\"} 2");
        assertThat(metrics).contains("eventcore_deliveries{status=\"delivered\"} 1");
        assertThat(metrics).contains("eventcore_deliveries{status=\"pending\"} 0");
    }

    @Test
    void ingestedEventsAreCounted() {
        insertEvent("metric.count", OffsetDateTime.now());
        insertEvent("metric.count", OffsetDateTime.now().minusSeconds(1));

        assertThat(scrapeMetrics()).contains("eventcore_events_ingested_total 2");
    }

    @Test
    void theOldestPendingDeliveryAgeIsReported() {
        UUID subscription = insertSubscription();
        insertDelivery(subscription, "pending", OffsetDateTime.now().minusSeconds(120));

        double age = metricValue(scrapeMetrics(), "eventcore_oldest_pending_delivery_age_seconds");

        assertThat(age).isBetween(115.0, 180.0);
    }

    @Test
    void deliveryAttemptsAreCountedByResult() {
        UUID subscription = insertSubscription();
        insertDelivery(subscription, "delivered", OffsetDateTime.now());
        UUID deliveryId = jdbc.sql("SELECT id FROM webhook_deliveries").query(UUID.class).single();
        insertAttempt(deliveryId, 1, 500);
        insertAttempt(deliveryId, 2, 200);

        String metrics = scrapeMetrics();

        assertThat(metrics).contains("eventcore_delivery_attempts_total{result=\"accepted\"} 1");
        assertThat(metrics).contains("eventcore_delivery_attempts_total{result=\"rejected\"} 1");
    }

    @Test
    void theLastReceivedTimestampIsReportedPerEventType() {
        OffsetDateTime lastOrder = OffsetDateTime.now().minusSeconds(30);
        insertEvent("order.placed", OffsetDateTime.now().minusSeconds(500));
        insertEvent("order.placed", lastOrder);
        insertEvent("invoice.paid", OffsetDateTime.now());

        String metrics = scrapeMetrics();

        assertThat(metrics).contains("# TYPE eventcore_event_last_received_timestamp_seconds gauge");
        double reported = metricValue(metrics,
                "eventcore_event_last_received_timestamp_seconds{type=\"order.placed\"}");
        assertThat(reported).isCloseTo(lastOrder.toEpochSecond(), org.assertj.core.data.Offset.offset(1.0));
        assertThat(metrics).contains("eventcore_event_last_received_timestamp_seconds{type=\"invoice.paid\"}");
    }

    private String scrapeMetrics() {
        return anonymousApi().get().uri("/metrics").retrieve().body(String.class);
    }

    private double metricValue(String metrics, String metricName) {
        return metrics.lines()
                .filter(line -> line.startsWith(metricName + " "))
                .map(line -> Double.parseDouble(line.substring(metricName.length() + 1)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(metricName + " not found in:\n" + metrics));
    }

    private UUID insertSubscription() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO webhook_subscriptions (id, url, secret) VALUES (:id, :url, :secret)")
                .param("id", id)
                .param("url", "https://example.com/hooks/metrics-test")
                .param("secret", "whsec_test")
                .update();
        return id;
    }

    private void insertDelivery(UUID subscription, String status, OffsetDateTime createdAt) {
        jdbc.sql("""
                INSERT INTO webhook_deliveries (event_id, subscription_id, body, status, created_at, next_attempt_at)
                VALUES (:eventId, :subscriptionId, CAST(:body AS jsonb), :status, :createdAt, NOW() + INTERVAL '1 hour')
                """)
                .param("eventId", UUID.randomUUID())
                .param("subscriptionId", subscription)
                .param("body", "{\"type\": \"metrics.test\"}")
                .param("status", status)
                .param("createdAt", createdAt)
                .update();
    }

    private void insertAttempt(UUID deliveryId, int attempt, int statusCode) {
        jdbc.sql("""
                INSERT INTO delivery_attempts (delivery_id, attempt, status_code, duration_ms)
                VALUES (:deliveryId, :attempt, :statusCode, 5)
                """)
                .param("deliveryId", deliveryId)
                .param("attempt", attempt)
                .param("statusCode", statusCode)
                .update();
    }

    private void insertEvent(String type, OffsetDateTime time) {
        jdbc.sql("INSERT INTO events (id, time, type) VALUES (:id, :time, :type)")
                .param("id", UUID.randomUUID())
                .param("time", time)
                .param("type", type)
                .update();
    }
}