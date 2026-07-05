package dev.eventcore.events;

import dev.eventcore.IntegrationTestBase;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventsSchemaTest extends IntegrationTestBase {

    @Autowired
    private JdbcClient jdbc;

    private record Column(String name, String type) {}

    @Test
    void eventsTableIsAHypertable() {
        Long registered = jdbc.sql("""
                        SELECT count(*) FROM timescaledb_information.hypertables
                        WHERE hypertable_name = 'events'
                        """)
                .query(Long.class)
                .single();

        assertThat(registered)
                .as("events should be registered as a TimescaleDB hypertable")
                .isEqualTo(1);
    }

    @Test
    void eventsTableHasExpectedColumns() {
        List<Column> columns = jdbc.sql("""
                        SELECT column_name, data_type FROM information_schema.columns
                        WHERE table_name = 'events'
                        """)
                .query((row, rowNumber) -> new Column(row.getString("column_name"), row.getString("data_type")))
                .list();

        assertThat(columns).containsExactlyInAnyOrder(
                new Column("id", "uuid"),
                new Column("time", "timestamp with time zone"),
                new Column("type", "text"),
                new Column("payload", "jsonb"));
    }
}