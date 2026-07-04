package dev.eventcore;

import java.time.OffsetDateTime;
import java.util.UUID;

record WebhookSubscription(UUID id, OffsetDateTime createdAt, String url) {

    static WebhookSubscription now(String url) {
        return new WebhookSubscription(UUID.randomUUID(), OffsetDateTime.now(), url);
    }
}
