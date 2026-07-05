package dev.eventcore.pull;

import dev.eventcore.api.Cursor;
import dev.eventcore.api.InvalidRequestException;
import dev.eventcore.events.EventTypes;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

record CreatePullSubscriptionRequest(String name, String from, List<String> eventTypes) {

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
            case "now" -> LogPositions.now();
            default -> LogPositions.atTimestamp(parsedTimestamp());
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
