package dev.eventcore.events;

import dev.eventcore.api.Cursor;
import dev.eventcore.api.InvalidRequestException;

record EventQuery(int limit, Cursor after, String type) {

    private static final int MAX_LIMIT = 200;

    static EventQuery of(int limit, String cursor, String type) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        return new EventQuery(limit, cursor == null ? null : Cursor.decode(cursor), normalized(type));
    }

    boolean startsFromTheTop() {
        return after == null;
    }

    boolean filtersByType() {
        return type != null;
    }

    int rowsToFetch() {
        return limit + 1;
    }

    private static String normalized(String type) {
        return type == null || type.isBlank() ? null : type;
    }
}