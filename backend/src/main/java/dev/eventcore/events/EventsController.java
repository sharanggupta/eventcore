package dev.eventcore.events;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Events", description = "Ingest events and query the log")
@RestController
@RequestMapping("/v1/events")
class EventsController {

    private final EventIngestion ingestion;
    private final EventStore events;

    EventsController(EventIngestion ingestion, EventStore events) {
        this.ingestion = ingestion;
        this.events = events;
    }

@Operation(summary = "Ingest an event; type is required, payload is arbitrary JSON")
    @PostMapping    @ResponseStatus(HttpStatus.CREATED)
    EventCreated create(@RequestBody CreateEventRequest request) {
        request.validate();
        return ingestion.ingest(request.type(), request.payload());
    }

@Operation(summary = "List events newest-first; filter by type, time range (from/to), and payload fields (payload.<field>=<value>, dotted paths for nesting)")
    @GetMapping    EventPage list(@RequestParam(defaultValue = "50") int limit,
                   @RequestParam(required = false) String cursor,
                   @RequestParam(required = false) String type,
                   @RequestParam(required = false) String from,
                   @RequestParam(required = false) String to,
                   @RequestParam Map<String, String> allParams) {
        return events.page(EventQuery.of(limit, cursor, type, from, to, allParams));
    }
}