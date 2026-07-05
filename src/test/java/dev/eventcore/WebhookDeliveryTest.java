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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        wipeAllData();
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

        JsonNode delivered = json.readTree(subscriber.inbox("first").getFirst().body());
        assertThat(delivered.get("type").asText()).isEqualTo("order.placed");
        assertThat(delivered.get("payload").get("orderId").asText()).isEqualTo("42");
    }

    @Test
    void deliveriesAreSignedWithTheSubscriptionSecret() throws GeneralSecurityException {
        RegisteredWebhook webhook = subscribe("signed");

        postEvent("payment.captured", null);

        await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(subscriber.inbox("signed")).hasSize(1));
        SignedCall call = subscriber.inbox("signed").getFirst();
        assertThat(call.signature()).isEqualTo(hmacSignatureOf(webhook.secret(), call.body()));
    }

    @Test
    void aSuccessfulDeliveryRecordsItsAttempt() {
        subscribe("recorded");

        postEvent("order.packed", null);

        await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(subscriber.inbox("recorded")).hasSize(1));
        DeliveryDetail detail = theOnlyDeliveryDetail();
        assertThat(detail.deliveryAttempts()).hasSize(1);
        DeliveryAttempt attempt = detail.deliveryAttempts().getFirst();
        assertThat(attempt.attempt()).isEqualTo(1);
        assertThat(attempt.statusCode()).isEqualTo(200);
        assertThat(attempt.error()).isNull();
        assertThat(attempt.attemptedAt()).isNotNull();
        assertThat(attempt.durationMs()).isNotNegative();
    }

    @Test
    void everyRetryIsRecordedWithItsFailureCause() {
        subscriber.failNextAttempts(4);
        subscribe("retried");

        postEvent("order.delayed", null);

        await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(subscriber.inbox("retried")).hasSize(1));
        DeliveryDetail detail = theOnlyDeliveryDetail();
        assertThat(detail.deliveryAttempts()).hasSize(5);
        assertThat(detail.deliveryAttempts()).extracting(DeliveryAttempt::attempt)
                .containsExactly(1, 2, 3, 4, 5);
        DeliveryAttempt firstFailure = detail.deliveryAttempts().getFirst();
        assertThat(firstFailure.statusCode()).isEqualTo(500);
        assertThat(firstFailure.responseSnippet()).contains("consumer exploded");
        assertThat(detail.deliveryAttempts().getLast().statusCode()).isEqualTo(200);
    }

    @Test
    void redeliveringAFailedDeliveryRunsAFreshCycle() {
        subscriber.failNextAttempts(Integer.MAX_VALUE);
        subscribe("recovering");
        postEvent("order.stuck", null);
        await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(theOnlyDelivery()).isEqualTo(new DeliveryRecord("failed", 5)));

        subscriber.failNextAttempts(0);
        var receipt = api().post()
                .uri("/v1/deliveries/" + theOnlyDeliveryDetail().id() + "/redeliver")
                .retrieve()
                .toEntity(RedeliveryReceipt.class);

        assertThat(receipt.getStatusCode().value()).isEqualTo(202);
        assertThat(receipt.getBody().status()).isEqualTo("pending");
        await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(theOnlyDelivery()).isEqualTo(new DeliveryRecord("delivered", 6)));
        assertThat(theOnlyDeliveryDetail().deliveryAttempts().getLast().statusCode()).isEqualTo(200);
    }

    @Test
    void aStillBrokenConsumerGetsAFreshCycleThenFailsAgain() {
        subscriber.failNextAttempts(Integer.MAX_VALUE);
        subscribe("hopeless");
        postEvent("order.doomed", null);
        await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(theOnlyDelivery()).isEqualTo(new DeliveryRecord("failed", 5)));

        api().post()
                .uri("/v1/deliveries/" + theOnlyDeliveryDetail().id() + "/redeliver")
                .retrieve()
                .toBodilessEntity();

        await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(theOnlyDelivery()).isEqualTo(new DeliveryRecord("failed", 10)));
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

    private RegisteredWebhook subscribe(String inbox) {
        return api().post()
                .uri("/v1/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"url\": \"" + subscriber.urlFor(inbox) + "\"}")
                .retrieve()
                .body(RegisteredWebhook.class);
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

    private DeliveryDetail theOnlyDeliveryDetail() {
        UUID id = jdbc.sql("SELECT id FROM webhook_deliveries").query(UUID.class).single();
        return api().get().uri("/v1/deliveries/" + id).retrieve().body(DeliveryDetail.class);
    }

    private String hmacSignatureOf(String secret, String body) throws GeneralSecurityException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(hmac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    record SignedCall(String body, String signature) {}

    @RestController
    static class StubSubscriber {

        private final Map<String, List<SignedCall>> inboxes = new ConcurrentHashMap<>();
        private final AtomicInteger failuresRemaining = new AtomicInteger();

        private int port;

        @PostMapping("/stub/{inbox}")
        ResponseEntity<String> receive(@PathVariable String inbox,
                                       @RequestHeader(value = "X-EventCore-Signature", required = false) String signature,
                                       @RequestBody String body) {
            if (failuresRemaining.getAndUpdate(remaining -> Math.max(0, remaining - 1)) > 0) {
                return ResponseEntity.internalServerError().body("{\"error\": \"consumer exploded\"}");
            }
            inbox(inbox).add(new SignedCall(body, signature));
            return ResponseEntity.ok().build();
        }

        List<SignedCall> inbox(String name) {
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
