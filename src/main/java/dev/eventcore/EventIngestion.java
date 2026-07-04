package dev.eventcore;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
class EventIngestion {

    private final EventStore events;
    private final DeliveryOutbox outbox;

    EventIngestion(EventStore events, DeliveryOutbox outbox) {
        this.events = events;
        this.outbox = outbox;
    }

    @Transactional
    EventCreated ingest(String type, JsonNode payload) {
        EventCreated receipt = events.append(type, payload);
        outbox.enqueue(new Event(receipt.id(), receipt.time(), receipt.type(), payload));
        return receipt;
    }
}
