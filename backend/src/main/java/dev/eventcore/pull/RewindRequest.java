package dev.eventcore.pull;

import dev.eventcore.api.Cursor;

record RewindRequest(String to) {

    /** The target position; {@code "beginning"} is null, otherwise an ISO-8601 timestamp. */
    Cursor target() {
        return LogPositions.parse(to, false, "to must be \"beginning\" or an ISO-8601 timestamp");
    }
}
