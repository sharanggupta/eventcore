package dev.eventcore.deliveries;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
class WebhookDispatcher {

    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(10);
    private static final int SNIPPET_MAX_BYTES = 512;

    private final DeliveryOutbox outbox;
    private final RestClient http;

    WebhookDispatcher(DeliveryOutbox outbox) {
        this.outbox = outbox;
        this.http = buildHttpClient();
    }

    @Scheduled(fixedDelayString = "${eventcore.webhooks.poll-interval}")
    void deliverDueWebhooks() {
        outbox.due().forEach(this::deliver);
    }

    private void deliver(PendingDelivery delivery) {
        AttemptOutcome outcome = post(delivery);
        if (outcome.accepted()) {
            outbox.recordSuccess(delivery, outcome);
        } else {
            outbox.recordFailure(delivery, outcome);
        }
    }

    private AttemptOutcome post(PendingDelivery delivery) {
        long startedAt = System.nanoTime();
        try {
            return http.post()
                    .uri(delivery.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-EventCore-Signature", delivery.signature())
                    .body(delivery.body())
                    .exchange((request, response) -> new AttemptOutcome(
                            response.getStatusCode().value(),
                            null,
                            snippetOf(response.getBody().readNBytes(SNIPPET_MAX_BYTES)),
                            millisSince(startedAt)));
        } catch (RestClientException rejectedOrUnreachable) {
            return new AttemptOutcome(null, rejectedOrUnreachable.getMessage(), null, millisSince(startedAt));
        }
    }

    private static String snippetOf(byte[] head) throws IOException {
        return head.length == 0 ? null : new String(head, StandardCharsets.UTF_8);
    }

    private static long millisSince(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
    }

    private static RestClient buildHttpClient() {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(DELIVERY_TIMEOUT);
        requests.setReadTimeout(DELIVERY_TIMEOUT);
        return RestClient.builder().requestFactory(requests).build();
    }
}