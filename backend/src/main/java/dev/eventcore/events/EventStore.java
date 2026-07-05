package dev.eventcore.events;

import dev.eventcore.api.Cursor;

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
public class EventStore {

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

    /** Oldest-first read for pull consumers; a null position means the beginning of the log. */
    public List<Event> ascendingAfter(Cursor position, List<String> types, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_EVENTS + " WHERE TRUE");
        if (position != null) {
            sql.append(" AND (time, id) > (:time, :id)");
        }
        if (types != null) {
            sql.append(" AND type IN (:types)");
        }
        sql.append(" ORDER BY time ASC, id ASC LIMIT :limit");
        var statement = jdbc.sql(sql.toString()).param("limit", limit);
        if (position != null) {
            statement = statement.param("time", position.time()).param("id", position.id());
        }
        if (types != null) {
            statement = statement.param("types", types);
        }
        return statement.query(this::toEvent).list();
    }

    /** How many events lie after the position; a pull consumer's lag. */
    public long countAfter(Cursor position, List<String> types) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM events WHERE TRUE");
        if (position != null) {
            sql.append(" AND (time, id) > (:time, :id)");
        }
        if (types != null) {
            sql.append(" AND type IN (:types)");
        }
        var statement = jdbc.sql(sql.toString());
        if (position != null) {
            statement = statement.param("time", position.time()).param("id", position.id());
        }
        if (types != null) {
            statement = statement.param("types", types);
        }
        return statement.query(Long.class).single();
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
        if (query.boundedBelow()) {
            sql.append(" AND time >= :from");
        }
        if (query.boundedAbove()) {
            sql.append(" AND time <= :to");
        }
        for (int i = 0; i < query.payloadFilters().size(); i++) {
            sql.append(" AND payload #>> CAST(:payloadPath").append(i)
                    .append(" AS text[]) = :payloadValue").append(i);
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
        if (query.boundedBelow()) {
            statement = statement.param("from", query.from());
        }
        if (query.boundedAbove()) {
            statement = statement.param("to", query.to());
        }
        List<PayloadFilter> filters = query.payloadFilters();
        for (int i = 0; i < filters.size(); i++) {
            statement = statement.param("payloadPath" + i, filters.get(i).pathLiteral())
                    .param("payloadValue" + i, filters.get(i).value());
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