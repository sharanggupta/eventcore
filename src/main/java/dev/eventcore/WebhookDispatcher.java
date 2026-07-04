package dev.eventcore;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class WebhookDispatcher {

    private final DeliveryOutbox outbox;
    private final RestClient http;

    WebhookDispatcher(DeliveryOutbox outbox) {
        this.outbox = outbox;
        this.http = RestClient.create();
    }

    @Scheduled(fixedDelayString = "${eventcore.webhooks.poll-interval}")
    void deliverDueWebhooks() {
        outbox.due().forEach(this::deliver);
    }

    private void deliver(PendingDelivery delivery) {
        if (accepted(delivery)) {
            outbox.recordSuccess(delivery);
        } else {
            outbox.recordFailure(delivery);
        }
    }

    private boolean accepted(PendingDelivery delivery) {
        try {
            http.post()
                    .uri(delivery.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(delivery.body())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException rejectedOrUnreachable) {
            return false;
        }
    }
}
