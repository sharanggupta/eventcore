package dev.eventcore.pull;

import dev.eventcore.api.ConflictException;
import dev.eventcore.api.Cursor;
import dev.eventcore.api.NotFoundException;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
class PullSubscriptionStore {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    PullSubscriptionStore(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    PullSubscription create(String name, Cursor startingPoint, List<String> eventTypes) {
        try {
            jdbc.sql("""
                    INSERT INTO pull_subscriptions (name, position_time, position_id, event_types)
                    VALUES (:name, :positionTime, :positionId, CAST(:eventTypes AS jsonb))
                    """)
                    .param("name", name)
                    .param("positionTime", startingPoint == null ? null : startingPoint.time(),
                            Types.TIMESTAMP_WITH_TIMEZONE)
                    .param("positionId", startingPoint == null ? null : startingPoint.id(),
                            Types.OTHER)
                    .param("eventTypes", asJson(eventTypes), Types.VARCHAR)
                    .update();
        } catch (DuplicateKeyException taken) {
            throw new ConflictException("a pull subscription named \"" + name + "\" already exists");
        }
        return one(name);
    }

    PullSubscription one(String name) {
        return jdbc.sql("SELECT name, position_time, position_id, event_types::text AS event_types, "
                        + "created_at FROM pull_subscriptions WHERE name = :name")
                .param("name", name)
                .query(this::toSubscription)
                .optional()
                .orElseThrow(() -> new NotFoundException("pull subscription not found"));
    }

    Cursor positionOf(String name) {
        PullSubscription subscription = one(name);
        return subscription.position() == null ? null : Cursor.decode(subscription.position());
    }

    List<String> subscribedTypesOf(String name) {
        return one(name).eventTypes();
    }

    PullSubscription commit(String name, Cursor position) {
        int updated = jdbc.sql("UPDATE pull_subscriptions "
                        + "SET position_time = :time, position_id = :id WHERE name = :name")
                .param("name", name)
                .param("time", position.time())
                .param("id", position.id())
                .update();
        if (updated == 0) {
            throw new NotFoundException("pull subscription not found");
        }
        return one(name);
    }

    private PullSubscription toSubscription(ResultSet row, int rowNumber) throws SQLException {
        OffsetDateTime positionTime = row.getObject("position_time", OffsetDateTime.class);
        UUID positionId = row.getObject("position_id", UUID.class);
        String position = positionTime == null ? null : new Cursor(positionTime, positionId).encode();
        return new PullSubscription(
                row.getString("name"),
                position,
                asEventTypes(row.getString("event_types")),
                row.getObject("created_at", OffsetDateTime.class));
    }

    private String asJson(List<String> eventTypes) {
        return eventTypes == null ? null : json.writeValueAsString(eventTypes);
    }

    private List<String> asEventTypes(String stored) {
        return stored == null ? null : List.of(json.readValue(stored, String[].class));
    }
}
