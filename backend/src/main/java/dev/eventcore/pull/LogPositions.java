package dev.eventcore.pull;

import dev.eventcore.api.Cursor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** The shared vocabulary for "where in the log": beginning is null, everything else is a Cursor. */
final class LogPositions {

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
}
