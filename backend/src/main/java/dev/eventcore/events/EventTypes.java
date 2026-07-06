package dev.eventcore.events;

import dev.eventcore.api.Wildcards;

import java.util.List;

/**
 * The event-type routing filter: absent / empty / {@code ["*"]} mean "every type" (null internally).
 * The null↔{@code ["*"]}↔storage mapping is owned by {@link Wildcards}; this is its event-type facing name.
 */
public final class EventTypes {

    private EventTypes() {
    }

    /** Returns null for "receive everything"; rejects the wildcard mixed with specifics, and blanks. */
    public static List<String> normalized(List<String> eventTypes) {
        return Wildcards.normalized(eventTypes,
                "\"*\" means all event types and cannot be combined with specific types",
                "event types must not be blank");
    }

    /** The wire form: null ("all") becomes {@code ["*"]}. */
    public static List<String> wire(List<String> eventTypes) {
        return Wildcards.wire(eventTypes);
    }
}
