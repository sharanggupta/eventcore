package dev.eventcore.events;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/events")
class EventsController {

    private final EventIngestion ingestion;
    private final EventStore events;

    EventsController(EventIngestion ingestion, EventStore events) {
        this.ingestion = ingestion;
        this.events = events;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EventCreated create(@RequestBody CreateEventRequest request) {
        request.validate();
        return ingestion.ingest(request.type(), request.payload());
    }

    @GetMapping
    EventPage list(@RequestParam(defaultValue = "50") int limit,
                   @RequestParam(required = false) String cursor,
                   @RequestParam(required = false) String type) {
        return events.page(EventQuery.of(limit, cursor, type));
    }
}