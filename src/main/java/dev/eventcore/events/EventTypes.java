package dev.eventcore.events;

import dev.eventcore.api.InvalidRequestException;

import java.util.List;

public final class EventTypes {

    private EventTypes() {
    }

    /** Returns null for "receive everything"; rejects blank entries. */
    public static List<String> normalized(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return null;
        }
        if (eventTypes.stream().anyMatch(type -> type == null || type.isBlank())) {
            throw new InvalidRequestException("event types must not be blank");
        }
        return List.copyOf(eventTypes);
    }
}