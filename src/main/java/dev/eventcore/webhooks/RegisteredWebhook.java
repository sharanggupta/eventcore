package dev.eventcore.webhooks;

import dev.eventcore.crypto.Secrets;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RegisteredWebhook(UUID id, OffsetDateTime createdAt, String url, List<String> eventTypes,
                         String secret) {

    static RegisteredWebhook now(String url, List<String> eventTypes) {
        return new RegisteredWebhook(UUID.randomUUID(), OffsetDateTime.now(), url, eventTypes,
                Secrets.withPrefix("whsec_"));
    }
}