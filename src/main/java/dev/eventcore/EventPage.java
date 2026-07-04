package dev.eventcore;

import java.util.List;

record EventPage(List<Event> items, String nextCursor) {

    static EventPage from(List<Event> fetched, int limit) {
        if (fetched.size() <= limit) {
            return new EventPage(fetched, null);
        }
        List<Event> items = fetched.subList(0, limit);
        return new EventPage(items, Cursor.after(items.getLast()).encode());
    }
}
