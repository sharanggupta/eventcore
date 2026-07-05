package dev.eventcore.retention;

import dev.eventcore.IntegrationTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "eventcore.retention.events-max-age=30d",
        "eventcore.retention.delivery-history-max-age=7d"
})
class RetentionTest extends IntegrationTestBase {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RetentionSweeper sweeper;

    @BeforeEach
    void startClean() {
        wipeAllData();
    }

    @Test
    void eventsOlderThanTheRetentionWindowAreDropped() {
        insertEvent("ancient.event", OffsetDateTime.now().minusDays(100));
        insertEvent("recent.event", OffsetDateTime.now());

        sweeper.sweepNow();

        assertThat(eventTypes()).containsExactly("recent.event");
    }

    @Test
    void deliveryHistoryOlderThanItsWindowIsDeletedWithItsAttempts() {
        UUID subscription = insertSubscription();
        UUID oldDelivery = insertDelivery(subscription, OffsetDateTime.now().minusDays(30));
        insertAttempt(oldDelivery);
        UUID freshDelivery = insertDelivery(subscription, OffsetDateTime.now());

        sweeper.sweepNow();

        assertThat(deliveryIds()).containsExactly(freshDelivery);
        assertThat(count("delivery_attempts")).isZero();
        assertThat(count("webhook_subscriptions")).isEqualTo(1);
    }

    @Test
    void sweepingTwiceIsHarmless() {
        insertEvent("survivor.event", OffsetDateTime.now());

        sweeper.sweepNow();
        sweeper.sweepNow();

        assertThat(eventTypes()).containsExactly("survivor.event");
    }

    private java.util.List<String> eventTypes() {
        return jdbc.sql("SELECT type FROM events ORDER BY time").query(String.class).list();
    }

    private java.util.List<UUID> deliveryIds() {
        return jdbc.sql("SELECT id FROM webhook_deliveries").query(UUID.class).list();
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private void insertEvent(String type, OffsetDateTime time) {
        jdbc.sql("INSERT INTO events (id, time, type) VALUES (:id, :time, :type)")
                .param("id", UUID.randomUUID())
                .param("time", time)
                .param("type", type)
                .update();
    }

    private UUID insertSubscription() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO webhook_subscriptions (id, url, secret) VALUES (:id, :url, :secret)")
                .param("id", id)
                .param("url", "https://example.com/hooks/retention-test")
                .param("secret", "whsec_test")
                .update();
        return id;
    }

    private UUID insertDelivery(UUID subscription, OffsetDateTime createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO webhook_deliveries
                    (id, event_id, subscription_id, body, status, created_at, next_attempt_at)
                VALUES (:id, :eventId, :subscriptionId, CAST(:body AS jsonb), 'delivered',
                        :createdAt, NOW() + INTERVAL '1 hour')
                """)
                .param("id", id)
                .param("eventId", UUID.randomUUID())
                .param("subscriptionId", subscription)
                .param("body", "{\"type\": \"retention.test\"}")
                .param("createdAt", createdAt)
                .update();
        return id;
    }

    private void insertAttempt(UUID deliveryId) {
        jdbc.sql("INSERT INTO delivery_attempts (delivery_id, attempt, status_code, duration_ms) "
                        + "VALUES (:deliveryId, 1, 200, 5)")
                .param("deliveryId", deliveryId)
                .update();
    }
}
