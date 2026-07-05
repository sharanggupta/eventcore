package dev.eventcore.webhooks;

import dev.eventcore.crypto.Secrets;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RegisteredWebhook(UUID id, OffsetDateTime createdAt, String url,
                                List<String> eventTypes, List<String> payloadFields,
                                String secret) {

    static RegisteredWebhook now(String url, List<String> eventTypes, List<String> payloadFields) {
        return new RegisteredWebhook(UUID.randomUUID(), OffsetDateTime.now(), url, eventTypes,
                payloadFields, Secrets.withPrefix("whsec_"));
    }
}
