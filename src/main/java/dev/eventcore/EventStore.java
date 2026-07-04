package dev.eventcore;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
class EventStore {

    private static final String SELECT_EVENTS =
            "SELECT id, time, type, payload::text AS payload FROM events";
    private static final String NEWEST_FIRST = " ORDER BY time DESC, id DESC LIMIT :limit";

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    EventStore(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    EventCreated append(String type, JsonNode payload) {
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

    EventPage page(EventQuery query) {
        List<Event> fetched = query.startsFromTheTop() ? topOfTheLog(query) : logAfter(query);
        return EventPage.from(fetched, query.limit());
    }

    private List<Event> topOfTheLog(EventQuery query) {
        return jdbc.sql(SELECT_EVENTS + NEWEST_FIRST)
                .param("limit", query.rowsToFetch())
                .query(this::toEvent)
                .list();
    }

    private List<Event> logAfter(EventQuery query) {
        return jdbc.sql(SELECT_EVENTS + " WHERE (time, id) < (:time, :id)" + NEWEST_FIRST)
                .param("time", query.after().time())
                .param("id", query.after().id())
                .param("limit", query.rowsToFetch())
                .query(this::toEvent)
                .list();
    }

    private Event toEvent(ResultSet row, int rowNumber) throws SQLException {
        return new Event(
                row.getObject("id", UUID.class),
                row.getObject("time", OffsetDateTime.class),
                row.getString("type"),
                asPayload(row.getString("payload")));
    }

    private String asJson(JsonNode payload) {
        return payload == null || payload.isNull() ? null : json.writeValueAsString(payload);
    }

    private JsonNode asPayload(String stored) {
        return stored == null ? null : json.readTree(stored);
    }
}
