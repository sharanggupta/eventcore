package dev.eventcore;

import java.time.OffsetDateTime;
import java.util.UUID;

record EventCreated(UUID id, OffsetDateTime time, String type) {

    static EventCreated now(String type) {
        return new EventCreated(UUID.randomUUID(), OffsetDateTime.now(), type);
    }
}
