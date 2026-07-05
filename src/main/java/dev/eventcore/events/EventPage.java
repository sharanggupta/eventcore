package dev.eventcore.events;

import dev.eventcore.api.Cursor;

import java.util.List;

public record EventPage(List<Event> items, String nextCursor) {

    static EventPage from(List<Event> fetched, int limit) {
        if (fetched.size() <= limit) {
            return new EventPage(fetched, null);
        }
        List<Event> items = fetched.subList(0, limit);
        Event last = items.getLast();
        return new EventPage(items, new Cursor(last.time(), last.id()).encode());
    }
}