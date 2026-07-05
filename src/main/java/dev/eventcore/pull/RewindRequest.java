package dev.eventcore.pull;

import dev.eventcore.api.Cursor;
import dev.eventcore.api.InvalidRequestException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

record RewindRequest(String to) {

    /** Null means the beginning of the log. */
    Cursor target() {
        if ("beginning".equals(to)) {
            return null;
        }
        try {
            return LogPositions.atTimestamp(OffsetDateTime.parse(to == null ? "" : to));
        } catch (DateTimeParseException malformed) {
            throw new InvalidRequestException("to must be \"beginning\" or an ISO-8601 timestamp");
        }
    }
}
