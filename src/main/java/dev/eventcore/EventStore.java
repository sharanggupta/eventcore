package dev.eventcore;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Types;

@Repository
class EventStore {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    EventStore(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    EventCreated save(String type, JsonNode payload) {
        EventCreated event = EventCreated.now(type);
        jdbc.sql("""
                INSERT INTO events (id, time, type, payload)
                VALUES (:id, :time, :type, CAST(:payload AS jsonb))
                """)
                .param("id", event.id())
                .param("time", event.time())
                .param("type", event.type())
                .param("payload", asJson(payload), Types.VARCHAR)
                .update();
        return event;
    }

    private String asJson(JsonNode payload) {
        return payload == null || payload.isNull() ? null : json.writeValueAsString(payload);
    }
}
