package dev.eventcore.pull;

import dev.eventcore.api.Cursor;
import dev.eventcore.api.InvalidRequestException;
import dev.eventcore.events.EventTypes;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

record CreatePullSubscriptionRequest(String name, String from, List<String> eventTypes) {

    private static final UUID FIRST_POSSIBLE_ID = new UUID(0, 0);
    private static final UUID LAST_POSSIBLE_ID = new UUID(-1, -1);

    void validate() {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("name is required");
        }
        startingPoint();
        subscribedTypes();
    }

    /** Null means the beginning of the log. Defaults to "now" when absent. */
    Cursor startingPoint() {
        return switch (from == null ? "now" : from) {
            case "beginning" -> null;
            case "now" -> new Cursor(OffsetDateTime.now(), LAST_POSSIBLE_ID);
            default -> new Cursor(parsedTimestamp(), FIRST_POSSIBLE_ID);
        };
    }

    List<String> subscribedTypes() {
        return EventTypes.normalized(eventTypes);
    }

    private OffsetDateTime parsedTimestamp() {
        try {
            return OffsetDateTime.parse(from);
        } catch (DateTimeParseException malformed) {
            throw new InvalidRequestException(
                    "from must be \"beginning\", \"now\", or an ISO-8601 timestamp");
        }
    }
}
