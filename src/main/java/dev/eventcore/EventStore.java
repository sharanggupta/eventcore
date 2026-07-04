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
        List<Event> fetched = bindConditions(jdbc.sql(sqlFor(query)), query)
                .param("limit", query.rowsToFetch())
                .query(this::toEvent)
                .list();
        return EventPage.from(fetched, query.limit());
    }

    private String sqlFor(EventQuery query) {
        StringBuilder sql = new StringBuilder(SELECT_EVENTS).append(" WHERE TRUE");
        if (query.filtersByType()) {
            sql.append(" AND type = :type");
        }
        if (!query.startsFromTheTop()) {
            sql.append(" AND (time, id) < (:time, :id)");
        }
        return sql.append(NEWEST_FIRST).toString();
    }

    private JdbcClient.StatementSpec bindConditions(JdbcClient.StatementSpec statement, EventQuery query) {
        if (query.filtersByType()) {
            statement = statement.param("type", query.type());
        }
        if (!query.startsFromTheTop()) {
            statement = statement.param("time", query.after().time()).param("id", query.after().id());
        }
        return statement;
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
