package dev.eventcore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class EventsSchemaTest extends IntegrationTestBase {

    @Autowired
    private DataSource dataSource;

    @Test
    void eventsTableIsAHypertable() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            var resultSet = connection.createStatement().executeQuery(
                    "SELECT hypertable_name FROM timescaledb_information.hypertables " +
                    "WHERE hypertable_name = 'events'");
            assertThat(resultSet.next())
                    .as("events should be registered as a TimescaleDB hypertable")
                    .isTrue();
        }
    }

    @Test
    void eventsTableHasExpectedColumns() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            var resultSet = connection.createStatement().executeQuery(
                    "SELECT column_name, data_type FROM information_schema.columns " +
                    "WHERE table_name = 'events' ORDER BY column_name");
            var columns = new java.util.HashMap<String, String>();
            while (resultSet.next()) {
                columns.put(resultSet.getString("column_name"), resultSet.getString("data_type"));
            }
            assertThat(columns).containsEntry("id", "uuid")
                    .containsEntry("time", "timestamp with time zone")
                    .containsEntry("type", "text")
                    .containsEntry("payload", "jsonb");
        }
    }
}
