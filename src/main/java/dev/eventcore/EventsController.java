package dev.eventcore;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/events")
class EventsController {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    EventsController(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    record EventRequest(String type, JsonNode payload) {}

    @PostMapping
    ResponseEntity<Map<String, Object>> create(@RequestBody EventRequest request) {
        if (request.type() == null || request.type().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "type is required"));
        }

        UUID id = UUID.randomUUID();
        OffsetDateTime time = OffsetDateTime.now();
        String payload = request.payload() == null || request.payload().isNull()
                ? null
                : objectMapper.writeValueAsString(request.payload());

        jdbc.sql("INSERT INTO events (id, time, type, payload) "
                        + "VALUES (:id, :time, :type, CAST(:payload AS jsonb))")
                .param("id", id)
                .param("time", time)
                .param("type", request.type())
                .param("payload", payload, Types.VARCHAR)
                .update();

        return ResponseEntity.status(201).body(Map.of(
                "id", id.toString(),
                "time", time.toString(),
                "type", request.type()));
    }
}
