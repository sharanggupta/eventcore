package dev.eventcore.webhooks;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.eventcore.crypto.Secrets;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** {@code eventTypes}/{@code payloadFields} are {@code ["*"]} for all types / the full payload, or a specific list. */
public record RegisteredWebhook(UUID id, OffsetDateTime createdAt, String url,
                                @Schema(description = "[\"*\"] delivers every event type; a specific list restricts deliveries to those types.")
                                List<String> eventTypes,
                                @Schema(description = "[\"*\"] delivers the full payload; a specific list sends only those fields.")
                                List<String> payloadFields,
                                String secret) {

    static RegisteredWebhook now(String url, List<String> eventTypes, List<String> payloadFields) {
        return new RegisteredWebhook(UUID.randomUUID(), OffsetDateTime.now(), url, eventTypes,
                payloadFields, Secrets.withPrefix("whsec_"));
    }
}
