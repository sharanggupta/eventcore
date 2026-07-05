package dev.eventcore;

import java.util.List;

final class EventTypes {

    private EventTypes() {
    }

    /** Returns null for "receive everything"; rejects blank entries. */
    static List<String> normalized(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return null;
        }
        if (eventTypes.stream().anyMatch(type -> type == null || type.isBlank())) {
            throw new InvalidRequestException("event types must not be blank");
        }
        return List.copyOf(eventTypes);
    }
}
