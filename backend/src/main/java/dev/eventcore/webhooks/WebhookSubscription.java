package dev.eventcore.webhooks;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record WebhookSubscription(UUID id, OffsetDateTime createdAt, String url,
                           List<String> eventTypes, List<String> payloadFields) {
}
