package dev.eventcore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
        jdbc.sql("""
                INSERT INTO webhook_deliveries (event_id, subscription_id, body, status, attempts, created_at)
                VALUES (:eventId, :subscriptionId, CAST(:body AS jsonb), :status, :attempts, :createdAt)
                """)
                .param("eventId", UUID.randomUUID())
                .param("subscriptionId", subscriptionId)
                .param("body", "{\"type\": \"outbox.test\"}")
                .param("status", status)
                .param("attempts", attempts)
                .param("createdAt", createdAt)
                .update();
    }
}
