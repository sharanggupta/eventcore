package dev.eventcore;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.sql.Types;

@RestController
@RequestMapping("/v1/events")
class EventsController {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    EventsController(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EventCreated create(@RequestBody CreateEventRequest request) {
        request.validate();
        return store(request);
    }

    private EventCreated store(CreateEventRequest request) {
        EventCreated event = EventCreated.now(request.type());
        jdbc.sql("""
                INSERT INTO events (id, time, type, payload)
                VALUES (:id, :time, :type, CAST(:payload AS jsonb))
                """)
                .param("id", event.id())
                .param("time", event.time())
                .param("type", event.type())
                .param("payload", payloadJson(request), Types.VARCHAR)
                .update();
        return event;
    }

    private String payloadJson(CreateEventRequest request) {
        return request.hasPayload() ? json.writeValueAsString(request.payload()) : null;
    }
}
