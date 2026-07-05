package dev.eventcore.deliveries;

import dev.eventcore.IntegrationTestBase;
import dev.eventcore.api.ApiError;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryQueryTest extends IntegrationTestBase {

    @Autowired
    private JdbcClient jdbc;

    private UUID subscriptionId;

    @BeforeEach
    void startWithACleanOutbox() {
        wipeAllData();
        subscriptionId = insertSubscription();
    }

    @Test
    void listingReturnsNewestDeliveriesFirstWithTheirState() {
        OffsetDateTime now = OffsetDateTime.now();
        insertDelivery("failed", 5, now.minusSeconds(2));
        insertDelivery("pending", 1, now.minusSeconds(1));
        insertDelivery("delivered", 1, now);

        DeliveryPage page = listDeliveries("");

        assertThat(page.items()).extracting(Delivery::status)
                .containsExactly("delivered", "pending", "failed");
        Delivery newest = page.items().getFirst();
        assertThat(newest.id()).isNotNull();
        assertThat(newest.eventId()).isNotNull();
        assertThat(newest.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(newest.attempts()).isEqualTo(1);
        assertThat(newest.createdAt()).isNotNull();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void filteringByStatusReturnsOnlyMatchingDeliveries() {
        OffsetDateTime now = OffsetDateTime.now();
        insertDelivery("failed", 5, now.minusSeconds(2));
        insertDelivery("delivered", 1, now.minusSeconds(1));
        insertDelivery("failed", 5, now);

        DeliveryPage page = listDeliveries("?status=failed");

        assertThat(page.items()).hasSize(2)
                .allSatisfy(delivery -> assertThat(delivery.status()).isEqualTo("failed"));
    }

    @Test
    void pagesWalkTheOutboxWithoutOverlapOrGaps() {
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < 3; i++) {
            insertDelivery("failed", 5, now.minusSeconds(i));
        }

        DeliveryPage firstPage = listDeliveries("?limit=2");
        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.nextCursor()).isNotNull();

        DeliveryPage lastPage = listDeliveries("?limit=2&cursor=" + firstPage.nextCursor());
        assertThat(lastPage.items()).hasSize(1);
        assertThat(lastPage.nextCursor()).isNull();
    }

    @Test
    void aDeliveryFromBeforeAttemptCaptureShowsAnEmptyHistory() {
        OffsetDateTime now = OffsetDateTime.now();
        insertDelivery("failed", 5, now);
        UUID id = onlyDeliveryId();

        DeliveryDetail detail = api().get().uri("/v1/deliveries/" + id)
                .retrieve().body(DeliveryDetail.class);

        assertThat(detail.id()).isEqualTo(id);
        assertThat(detail.attempts()).isEqualTo(5);
        assertThat(detail.deliveryAttempts()).isEmpty();
    }

    @Test
    void anUnknownDeliveryIs404() {
        ResponseEntity<ApiError> response = api().get().uri("/v1/deliveries/" + UUID.randomUUID())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error()).isEqualTo("delivery not found");
    }

    @Test
    void redeliveringANonFailedDeliveryIs409() {
        insertDelivery("delivered", 1, OffsetDateTime.now());

        ResponseEntity<ApiError> response = api().post()
                .uri("/v1/deliveries/" + onlyDeliveryId() + "/redeliver")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error()).isEqualTo("only failed deliveries can be redelivered");
    }

    @Test
    void redeliveringAnUnknownDeliveryIs404() {
        ResponseEntity<ApiError> response = api().post()
                .uri("/v1/deliveries/" + UUID.randomUUID() + "/redeliver")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void bulkRedeliveryRequeuesEveryFailedDeliveryAndReportsTheCount() {
        OffsetDateTime now = OffsetDateTime.now();
        insertDelivery("failed", 5, now.minusSeconds(2));
        insertDelivery("failed", 5, now.minusSeconds(1));
        insertDelivery("delivered", 1, now);

        RedeliveredBatch batch = bulkRedeliver("{\"status\": \"failed\"}");

        assertThat(batch.requeued()).isEqualTo(2);
        assertThat(listDeliveries("?status=failed").items()).isEmpty();
        assertThat(listDeliveries("?status=delivered").items()).hasSize(1);
    }

    @Test
    void bulkRedeliveryCanBeScopedToOneSubscription() {
        UUID otherSubscription = insertSubscription();
        insertDelivery("failed", 5, OffsetDateTime.now().minusSeconds(1));
        insertDeliveryFor(otherSubscription, "failed", 5, OffsetDateTime.now());

        RedeliveredBatch batch = bulkRedeliver(
                "{\"status\": \"failed\", \"subscriptionId\": \"" + otherSubscription + "\"}");

        assertThat(batch.requeued()).isEqualTo(1);
        assertThat(listDeliveries("?status=failed").items())
                .extracting(Delivery::subscriptionId)
                .containsExactly(subscriptionId);
    }

    @Test
    void bulkRedeliveryWithNothingMatchingReportsZero() {
        RedeliveredBatch batch = bulkRedeliver("{\"status\": \"failed\"}");

        assertThat(batch.requeued()).isZero();
    }

    @Test
    void bulkRedeliveryWithoutTheExplicitFailedStatusIsRejected() {
        ResponseEntity<ApiError> response = api().post().uri("/v1/deliveries/redeliver")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("status is required and must be \"failed\"");
    }

    @Test
    void aTimeRangeReturnsOnlyDeliveriesWithinIt() {
        OffsetDateTime now = OffsetDateTime.now();
        insertDelivery("delivered", 1, now.minusHours(3));
        insertDelivery("failed", 5, now.minusHours(2));
        insertDelivery("pending", 0, now.minusMinutes(1));

        DeliveryPage page = listDeliveries("?from=" + now.minusMinutes(150) + "&to=" + now.minusMinutes(60));

        assertThat(page.items()).extracting(Delivery::status).containsExactly("failed");
    }

    @Test
    void anUnknownStatusIsRejected() {
        ResponseEntity<ApiError> response = api().get().uri("/v1/deliveries?status=exploded")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error())
                .isEqualTo("status must be one of pending, delivered, failed");
    }

    private DeliveryPage listDeliveries(String query) {
        return api().get().uri("/v1/deliveries" + query).retrieve().body(DeliveryPage.class);
    }

    private RedeliveredBatch bulkRedeliver(String body) {
        return api().post().uri("/v1/deliveries/redeliver")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(RedeliveredBatch.class);
    }

    private UUID onlyDeliveryId() {
        return jdbc.sql("SELECT id FROM webhook_deliveries").query(UUID.class).single();
    }

    private UUID insertSubscription() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO webhook_subscriptions (id, url, secret) VALUES (:id, :url, :secret)")
                .param("id", id)
                .param("url", "https://example.com/hooks/outbox-test")
                .param("secret", "whsec_test")
                .update();
        return id;
    }

    private void insertDelivery(String status, int attempts, OffsetDateTime createdAt) {
        insertDeliveryFor(subscriptionId, status, attempts, createdAt);
    }

    private void insertDeliveryFor(UUID subscription, String status, int attempts, OffsetDateTime createdAt) {
        jdbc.sql("""
                INSERT INTO webhook_deliveries (event_id, subscription_id, body, status, attempts, created_at, next_attempt_at)
                VALUES (:eventId, :subscriptionId, CAST(:body AS jsonb), :status, :attempts, :createdAt, NOW() + INTERVAL '1 hour')
                """)
                .param("eventId", UUID.randomUUID())
                .param("subscriptionId", subscription)
                .param("body", "{\"type\": \"outbox.test\"}")
                .param("status", status)
                .param("attempts", attempts)
                .param("createdAt", createdAt)
                .update();
    }
}