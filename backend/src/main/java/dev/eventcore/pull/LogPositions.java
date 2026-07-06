package dev.eventcore.pull;

import dev.eventcore.api.Cursor;
import dev.eventcore.api.InvalidRequestException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/** The shared vocabulary for "where in the log": {@code "beginning"} is null, everything else is a Cursor. */
final class LogPositions {

    static final String BEGINNING = "beginning";
    static final String NOW = "now";

    private static final UUID FIRST_POSSIBLE_ID = new UUID(0, 0);
    private static final UUID LAST_POSSIBLE_ID = new UUID(-1, -1);

    private LogPositions() {
    }

    static Cursor now() {
        return new Cursor(OffsetDateTime.now(), LAST_POSSIBLE_ID);
    }

    static Cursor atTimestamp(OffsetDateTime timestamp) {
        return new Cursor(timestamp, FIRST_POSSIBLE_ID);
    }

    /**
     * Resolves a position spec once: {@code "beginning"} is null, {@code "now"} is the current end
     * (when the caller allows it), anything else is an ISO-8601 timestamp. The caller supplies the
     * rejection message its own vocabulary uses ("from" allows "now"; "to" does not).
     */
    static Cursor parse(String spec, boolean allowNow, String malformedMessage) {
        if (BEGINNING.equals(spec)) {
            return null;
        }
        if (allowNow && NOW.equals(spec)) {
            return now();
        }
        try {
            return atTimestamp(OffsetDateTime.parse(spec == null ? "" : spec));
        } catch (DateTimeParseException malformed) {
            throw new InvalidRequestException(malformedMessage);
        }
    }
}
