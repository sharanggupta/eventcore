package dev.eventcore;

import java.time.OffsetDateTime;

record DeliveryAttempt(int attempt, OffsetDateTime attemptedAt, Integer statusCode, String error,
                       String responseSnippet, long durationMs) {
}
