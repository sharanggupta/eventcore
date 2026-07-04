package dev.eventcore;

import java.util.UUID;

record PendingDelivery(UUID id, String url, String body) {
}
