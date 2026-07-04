package dev.eventcore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestPropertySource(properties = {
        "eventcore.webhooks.poll-interval=100ms",
        "eventcore.webhooks.retry-backoff=50ms"
})
@Import(WebhookDeliveryTest.StubSubscriber.class)
class WebhookDeliveryTest extends IntegrationTestBase {

    private static final Duration PATIENCE = Duration.ofSeconds(15);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private StubSubscriber subscriber;

    private record DeliveryRecord(String status, int attempts) {}

    @BeforeEach
    void startClean() {
        jdbc.sql("DELETE FROM webhook_deliveries").update();
        jdbc.sql("DELETE FROM webhook_subscriptions").update();
        jdbc.sql("DELETE FROM events").update();
        subscriber.reset();
        subscriber.listenOn(serverPort());
    }

    @Test
    void anIngestedEventIsDeliveredToEverySubscription() {
        subscribe("first");
        subscribe("second");

        postEvent("order.placed", "{\"orderId\": \"42\"}");

        await().atMost(PATIENCE).untilAsserted(() -> {
            assertThat(subscriber.inbox("first")).hasSize(1);
            assertThat(subscriber.inbox("second")).hasSize(1);
        });

        JsonNode delivered = json.readTree(subscriber.inbox("first").getFirst());
        assertThat(delivered.get("type").asText()).isEqualTo("order.placed");
        assertThat(delivered.get("payload").get("orderId").asText()).isEqualTo("42");
    }

    @Test
    void aFailingSubscriberIsRetriedUntilItAccepts() {
        subscriber.failNextAttempts(4);
        subscribe("flaky");

        postEvent("invoice.paid", null);

        await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(subscriber.inbox("flaky")).hasSize(1));
        assertThat(theOnlyDelivery()).isEqualTo(new DeliveryRecord("delivered", 5));
    }

    @Test
    void aPersistentlyFailingDeliveryGivesUpAfterFiveAttempts() {
        subscriber.failNextAttempts(Integer.MAX_VALUE);
        subscribe("dead");

        postEvent("user.churned", null);

        await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(theOnlyDelivery()).isEqualTo(new DeliveryRecord("failed", 5)));
        assertThat(subscriber.inbox("dead")).isEmpty();
    }

    private void subscribe(String inbox) {
        api().post()
                .uri("/v1/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"url\": \"" + subscriber.urlFor(inbox) + "\"}")
                .retrieve()
                .toBodilessEntity();
    }

    private void postEvent(String type, String payload) {
        String body = payload == null
                ? "{\"type\": \"" + type + "\"}"
                : "{\"type\": \"" + type + "\", \"payload\": " + payload + "}";
        api().post()
                .uri("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private DeliveryRecord theOnlyDelivery() {
        return jdbc.sql("SELECT status, attempts FROM webhook_deliveries")
                .query((row, rowNumber) -> new DeliveryRecord(row.getString("status"), row.getInt("attempts")))
                .single();
    }

    @RestController
    static class StubSubscriber {

        private final Map<String, List<String>> inboxes = new ConcurrentHashMap<>();
        private final AtomicInteger failuresRemaining = new AtomicInteger();

        private int port;

        @PostMapping("/stub/{inbox}")
        ResponseEntity<Void> receive(@PathVariable String inbox, @RequestBody String body) {
            if (failuresRemaining.getAndUpdate(remaining -> Math.max(0, remaining - 1)) > 0) {
                return ResponseEntity.internalServerError().build();
            }
            inbox(inbox).add(body);
            return ResponseEntity.ok().build();
        }

        List<String> inbox(String name) {
            return inboxes.computeIfAbsent(name, unused -> new CopyOnWriteArrayList<>());
        }

        void failNextAttempts(int failures) {
            failuresRemaining.set(failures);
        }

        String urlFor(String inbox) {
            return "http://localhost:" + port + "/stub/" + inbox;
        }

        void listenOn(int port) {
            this.port = port;
        }

        void reset() {
            inboxes.clear();
            failuresRemaining.set(0);
        }
    }
}
