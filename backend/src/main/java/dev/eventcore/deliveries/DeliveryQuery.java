package dev.eventcore.deliveries;

import dev.eventcore.api.Cursor;
import dev.eventcore.api.InvalidRequestException;
import dev.eventcore.api.TimeBounds;

import java.time.OffsetDateTime;

import java.util.Set;

record DeliveryQuery(int limit, Cursor after, String status, OffsetDateTime from, OffsetDateTime to) {

    private static final int MAX_LIMIT = 200;
    private static final Set<String> KNOWN_STATUSES = Set.of("pending", "delivered", "failed");

    static DeliveryQuery of(int limit, String cursor, String status, String from, String to) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        if (status != null && !KNOWN_STATUSES.contains(status)) {
            throw new InvalidRequestException("status must be one of pending, delivered, failed");
        }
        return new DeliveryQuery(limit, cursor == null ? null : Cursor.decode(cursor), status,
                TimeBounds.parsedOrNull(from, "from"), TimeBounds.parsedOrNull(to, "to"));
    }

    boolean startsFromTheTop() {
        return after == null;
    }

    boolean filtersByStatus() {
        return status != null;
    }

    boolean boundedBelow() {
        return from != null;
    }

    boolean boundedAbove() {
        return to != null;
    }

    int rowsToFetch() {
        return limit + 1;
    }
}