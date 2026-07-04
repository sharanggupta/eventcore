package dev.eventcore;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/events")
class EventsController {

    private final EventStore events;

    EventsController(EventStore events) {
        this.events = events;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EventCreated create(@RequestBody CreateEventRequest request) {
        request.validate();
        return events.save(request.type(), request.payload());
    }
}
