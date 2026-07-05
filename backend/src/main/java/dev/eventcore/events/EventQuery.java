package dev.eventcore.events;

import dev.eventcore.api.Cursor;
import dev.eventcore.api.InvalidRequestException;
import dev.eventcore.api.TimeBounds;

import java.time.OffsetDateTime;

record EventQuery(int limit, Cursor after, String type, OffsetDateTime from, OffsetDateTime to) {

    private static final int MAX_LIMIT = 200;

    static EventQuery of(int limit, String cursor, String type, String from, String to) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        return new EventQuery(limit, cursor == null ? null : Cursor.decode(cursor), normalized(type),
                TimeBounds.parsedOrNull(from, "from"), TimeBounds.parsedOrNull(to, "to"));
    }

    boolean startsFromTheTop() {
        return after == null;
    }

    boolean filtersByType() {
        return type != null;
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

    private static String normalized(String type) {
        return type == null || type.isBlank() ? null : type;
    }
}