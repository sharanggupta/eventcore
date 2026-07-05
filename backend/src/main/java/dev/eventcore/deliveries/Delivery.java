package dev.eventcore.deliveries;

import java.time.OffsetDateTime;
import java.util.UUID;

record Delivery(UUID id, UUID eventId, UUID subscriptionId, String status, int attempts,
                OffsetDateTime createdAt) {
}