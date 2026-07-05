package dev.eventcore;

import java.util.UUID;

record PendingDelivery(UUID id, String url, String body, String secret, int attempts) {

    String signature() {
        return "sha256=" + HmacSha256.hexOf(secret, body);
    }

    int attemptNumber() {
        return attempts + 1;
    }
}
