package dev.eventcore;

import java.time.OffsetDateTime;
import java.util.UUID;

record RegisteredWebhook(UUID id, OffsetDateTime createdAt, String url, String secret) {

    static RegisteredWebhook now(String url) {
        return new RegisteredWebhook(UUID.randomUUID(), OffsetDateTime.now(), url,
                Secrets.withPrefix("whsec_"));
    }
}
