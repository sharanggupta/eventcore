package dev.eventcore.pull;

import dev.eventcore.api.Cursor;
import dev.eventcore.api.InvalidRequestException;
import dev.eventcore.events.EventTypes;

import java.util.List;

record CreatePullSubscriptionRequest(String name, String from, List<String> eventTypes) {

    void validate() {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("name is required");
        }
    }

    /** Resolves the starting cursor once (and validates {@code from}); absent means "now", null means the beginning. */
    Cursor startingPoint() {
        return LogPositions.parse(from == null ? LogPositions.NOW : from, true,
                "from must be \"beginning\", \"now\", or an ISO-8601 timestamp");
    }

    List<String> subscribedTypes() {
        return EventTypes.normalized(eventTypes);
    }
}
