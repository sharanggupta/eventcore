package dev.eventcore.webhooks;

import dev.eventcore.IntegrationTestBase;
import dev.eventcore.api.ApiError;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSubscriptionsTest extends IntegrationTestBase {

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void startWithNoSubscriptions() {
        wipeAllData();
    }

    @Test
    void registeringAWebhookReturnsItAndPersistsIt() {
        var response = registerWebhook("""
                {"url": "https://example.com/hooks/orders"}
                """).toEntity(RegisteredWebhook.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RegisteredWebhook webhook = response.getBody();
        assertThat(webhook.id()).isNotNull();
        assertThat(webhook.createdAt()).isNotNull();
        assertThat(webhook.url()).isEqualTo("https://example.com/hooks/orders");

        assertThat(subscriptionCount()).isEqualTo(1);
    }

    @Test
    void aSubscriptionCanLimitTheDeliveredPayloadToChosenFields() {
        RegisteredWebhook minimal = registerWebhook("""
                {"url": "https://example.com/hooks/minimal", "payloadFields": ["orderId"]}
                """).body(RegisteredWebhook.class);
        assertThat(minimal.payloadFields()).containsExactly("orderId");
        assertThat(listWebhooks()).singleElement()
                .satisfies(listed -> assertThat(listed.payloadFields()).containsExactly("orderId"));

        postEventWithCardNumber("order.placed");

        String deliveredBody = deliveredBodyFor(minimal.id());
        assertThat(deliveredBody).contains("ord_7");
        assertThat(deliveredBody).doesNotContain("cardNumber");
        assertThat(deliveredBody).contains("order.placed");
    }

    @Test
    void aSubscriptionWithoutPayloadFieldsReceivesTheFullPayload() {
        RegisteredWebhook full = registerWebhook("""
                {"url": "https://example.com/hooks/full"}
                """).body(RegisteredWebhook.class);

        postEventWithCardNumber("order.paid");

        assertThat(deliveredBodyFor(full.id())).contains("cardNumber");
    }

    @Test
    void aBlankPayloadFieldIsRejected() {
        ResponseEntity<ApiError> response = registerWebhookExpectingRejection("""
                {"url": "https://example.com/hooks/x", "payloadFields": [" "]}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("payload fields must not be blank");
    }

    private void postEventWithCardNumber(String type) {
        api().post().uri("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"type\": \"" + type + "\", \"payload\": {\"orderId\": \"ord_7\", \"cardNumber\": \"4242-4242\"}}")
                .retrieve()
                .toBodilessEntity();
    }

    private String deliveredBodyFor(UUID subscription) {
        return jdbc.sql("SELECT body::text FROM webhook_deliveries WHERE subscription_id = :id")
                .param("id", subscription)
                .query(String.class)
                .single();
    }

    @Test
    void registeringAWebhookRevealsItsSigningSecretExactlyOnce() {
        RegisteredWebhook webhook = registerWebhook("""
                {"url": "https://example.com/hooks/signed"}
                """).body(RegisteredWebhook.class);

        assertThat(webhook.secret()).startsWith("whsec_");

        String listedAsJson = api().get().uri("/v1/webhooks").retrieve().body(String.class);
        assertThat(listedAsJson).doesNotContain("secret").doesNotContain("whsec_");
    }

    @Test
    void listingReturnsAllRegisteredWebhooks() {
        registerWebhook("""
                {"url": "https://example.com/hooks/a"}
                """).toBodilessEntity();
        registerWebhook("""
                {"url": "https://example.com/hooks/b"}
                """).toBodilessEntity();

        List<WebhookSubscription> subscriptions = listWebhooks();

        assertThat(subscriptions).extracting(WebhookSubscription::url)
                .containsExactlyInAnyOrder("https://example.com/hooks/a", "https://example.com/hooks/b");
    }

    @Test
    void deletingASubscriptionRemovesIt() {
        RegisteredWebhook subscription = registerWebhook("""
                {"url": "https://example.com/hooks/short-lived"}
                """).body(RegisteredWebhook.class);

        var response = api().delete().uri("/v1/webhooks/" + subscription.id())
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(listWebhooks()).isEmpty();
    }

    @Test
    void deletingASubscriptionAlsoRemovesItsDeliveryHistory() {
        RegisteredWebhook subscription = registerWebhook("""
                {"url": "https://example.com/hooks/with-history"}
                """).body(RegisteredWebhook.class);
        postEvent("history.maker");

        var response = api().delete().uri("/v1/webhooks/" + subscription.id())
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(deliveryCount()).isZero();
    }

    @Test
    void deletingAnUnknownSubscriptionIs404() {
        var response = api().delete().uri("/v1/webhooks/" + UUID.randomUUID())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error()).isEqualTo("webhook subscription not found");
    }

    @Test
    void aFilteredSubscriptionEchoesItsEventTypes() {
        RegisteredWebhook webhook = registerWebhook("""
                {"url": "https://example.com/hooks/orders", "eventTypes": ["order.placed", "refund.issued"]}
                """).body(RegisteredWebhook.class);

        assertThat(webhook.eventTypes()).containsExactly("order.placed", "refund.issued");
        assertThat(listWebhooks()).singleElement()
                .satisfies(listed -> assertThat(listed.eventTypes())
                        .containsExactly("order.placed", "refund.issued"));
    }

    @Test
    void onlyMatchingEventsAreEnqueuedForAFilteredSubscription() {
        RegisteredWebhook filtered = registerWebhook("""
                {"url": "https://example.com/hooks/orders-only", "eventTypes": ["order.placed"]}
                """).body(RegisteredWebhook.class);
        RegisteredWebhook unfiltered = registerWebhook("""
                {"url": "https://example.com/hooks/everything"}
                """).body(RegisteredWebhook.class);

        postEvent("order.placed");
        postEvent("user.created");

        assertThat(deliveryCountFor(filtered.id())).isEqualTo(1);
        assertThat(deliveryCountFor(unfiltered.id())).isEqualTo(2);
    }

    @Test
    void aBlankEventTypeIsRejected() {
        ResponseEntity<ApiError> response = registerWebhookExpectingRejection("""
                {"url": "https://example.com/hooks/x", "eventTypes": ["order.placed", "  "]}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("event types must not be blank");
    }

    @Test
    void updatingTheFilterKeepsTheSubscriptionIdAndSecret() {
        RegisteredWebhook webhook = registerWebhook("""
                {"url": "https://example.com/hooks/orders", "eventTypes": ["order.placed"]}
                """).body(RegisteredWebhook.class);
        String secretBefore = storedSecretOf(webhook.id());

        WebhookSubscription updated = api().patch().uri("/v1/webhooks/" + webhook.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"eventTypes\": [\"order.placed\", \"return.received\"]}")
                .retrieve()
                .body(WebhookSubscription.class);

        assertThat(updated.id()).isEqualTo(webhook.id());
        assertThat(updated.eventTypes()).containsExactly("order.placed", "return.received");
        assertThat(storedSecretOf(webhook.id())).isEqualTo(secretBefore);

        postEvent("return.received");
        assertThat(deliveryCountFor(webhook.id())).isEqualTo(1);
    }

    @Test
    void updatingFiltersCanChangeThePayloadAllowListToo() {
        RegisteredWebhook webhook = registerWebhook("""
                {"url": "https://example.com/hooks/evolving", "payloadFields": ["orderId"]}
                """).body(RegisteredWebhook.class);

        WebhookSubscription updated = api().patch().uri("/v1/webhooks/" + webhook.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"eventTypes\": [\"order.placed\"], \"payloadFields\": [\"orderId\", \"item\"]}")
                .retrieve()
                .body(WebhookSubscription.class);

        assertThat(updated.eventTypes()).containsExactly("order.placed");
        assertThat(updated.payloadFields()).containsExactly("orderId", "item");

        postEventWithCardNumber("order.placed");
        assertThat(deliveredBodyFor(webhook.id())).contains("orderId").doesNotContain("cardNumber");
    }

    @Test
    void updatingTheFilterOfAnUnknownSubscriptionIs404() {
        ResponseEntity<ApiError> response = api().patch().uri("/v1/webhooks/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"eventTypes\": [\"order.placed\"]}")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error()).isEqualTo("webhook subscription not found");
    }

    @Test
    void missingUrlIsRejected() {
        ResponseEntity<ApiError> response = registerWebhookExpectingRejection("{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("url is required");
    }

    @Test
    void nonHttpUrlIsRejected() {
        ResponseEntity<ApiError> response = registerWebhookExpectingRejection("""
                {"url": "ftp://example.com/hook"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("url must be a valid http(s) URL");
    }

    private RestClient.ResponseSpec registerWebhook(String body) {
        return api().post()
                .uri("/v1/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve();
    }

    private ResponseEntity<ApiError> registerWebhookExpectingRejection(String body) {
        return registerWebhook(body)
                .onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(ApiError.class);
    }

    private List<WebhookSubscription> listWebhooks() {
        return api().get().uri("/v1/webhooks").retrieve()
                .body(new ParameterizedTypeReference<>() { });
    }

    private void postEvent(String type) {
        api().post()
                .uri("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"type\": \"" + type + "\"}")
                .retrieve()
                .toBodilessEntity();
    }

    private Long subscriptionCount() {
        return jdbc.sql("SELECT count(*) FROM webhook_subscriptions").query(Long.class).single();
    }

    private Long deliveryCount() {
        return jdbc.sql("SELECT count(*) FROM webhook_deliveries").query(Long.class).single();
    }

    private Long deliveryCountFor(UUID subscription) {
        return jdbc.sql("SELECT count(*) FROM webhook_deliveries WHERE subscription_id = :id")
                .param("id", subscription)
                .query(Long.class)
                .single();
    }

    private String storedSecretOf(UUID subscription) {
        return jdbc.sql("SELECT secret FROM webhook_subscriptions WHERE id = :id")
                .param("id", subscription)
                .query(String.class)
                .single();
    }
}