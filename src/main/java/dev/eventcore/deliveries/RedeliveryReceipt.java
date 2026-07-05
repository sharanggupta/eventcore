package dev.eventcore.deliveries;

import java.time.OffsetDateTime;
import java.util.UUID;

record RedeliveryReceipt(UUID id, String status, OffsetDateTime nextAttemptAt) {
}