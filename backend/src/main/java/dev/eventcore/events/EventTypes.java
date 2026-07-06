package dev.eventcore.events;

import dev.eventcore.api.InvalidRequestException;

import java.util.List;

public final class EventTypes {

    /** The wildcard that means "every event type", on the wire and in the request. */
    public static final String ALL = "*";

    private EventTypes() {
    }

    /** Returns null for "receive everything" (absent, empty, or the {@code ["*"]} wildcard); rejects blank entries. */
    public static List<String> normalized(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty() || eventTypes.equals(List.of(ALL))) {
            return null;
        }
        if (eventTypes.contains(ALL)) {
            throw new InvalidRequestException("\"*\" means all event types and cannot be combined with specific types");
        }
        if (eventTypes.stream().anyMatch(type -> type == null || type.isBlank())) {
            throw new InvalidRequestException("event types must not be blank");
        }
        return List.copyOf(eventTypes);
    }

    /** The wire form: the internal null ("all") becomes the explicit {@code ["*"]} wildcard. */
    public static List<String> wire(List<String> eventTypes) {
        return eventTypes == null ? List.of(ALL) : eventTypes;
    }
}