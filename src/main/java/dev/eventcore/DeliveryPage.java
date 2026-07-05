package dev.eventcore;

import java.util.List;

record DeliveryPage(List<Delivery> items, String nextCursor) {

    static DeliveryPage from(List<Delivery> fetched, int limit) {
        if (fetched.size() <= limit) {
            return new DeliveryPage(fetched, null);
        }
        List<Delivery> items = fetched.subList(0, limit);
        Delivery last = items.getLast();
        return new DeliveryPage(items, new Cursor(last.createdAt(), last.id()).encode());
    }
}
