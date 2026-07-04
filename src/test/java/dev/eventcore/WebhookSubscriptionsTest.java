package dev.eventcore;

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
                """).toEntity(WebhookSubscription.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        WebhookSubscription subscription = response.getBody();
        assertThat(subscription.id()).isNotNull();
        assertThat(subscription.createdAt()).isNotNull();
        assertThat(subscription.url()).isEqualTo("https://example.com/hooks/orders");

        assertThat(subscriptionCount()).isEqualTo(1);
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
        WebhookSubscription subscription = registerWebhook("""
                {"url": "https://example.com/hooks/short-lived"}
                """).body(WebhookSubscription.class);

        var response = api().delete().uri("/v1/webhooks/" + subscription.id())
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(listWebhooks()).isEmpty();
    }

    @Test
    void deletingASubscriptionAlsoRemovesItsDeliveryHistory() {
        WebhookSubscription subscription = registerWebhook("""
                {"url": "https://example.com/hooks/with-history"}
                """).body(WebhookSubscription.class);
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
}
