package dev.eventcore.pull;

import dev.eventcore.api.Cursor;
import dev.eventcore.events.Event;

import java.util.List;

public record PullBatch(List<Event> items, String nextCursor) {

    static PullBatch of(List<Event> items) {
        if (items.isEmpty()) {
            return new PullBatch(items, null);
        }
        Event last = items.getLast();
        return new PullBatch(items, new Cursor(last.time(), last.id()).encode());
    }
}
