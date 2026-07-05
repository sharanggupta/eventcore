package dev.eventcore.metrics;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
class PipelineMetrics {

    private static final List<String> DELIVERY_STATUSES = List.of("pending", "delivered", "failed");

    private final JdbcClient jdbc;

    PipelineMetrics(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Map<String, Long> deliveriesByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        DELIVERY_STATUSES.forEach(status -> counts.put(status, 0L));
        jdbc.sql("SELECT status, count(*) AS total FROM webhook_deliveries GROUP BY status")
                .query((row, rowNumber) -> counts.put(row.getString("status"), row.getLong("total")))
                .list();
        return counts;
    }

    long oldestPendingDeliveryAgeSeconds() {
        return jdbc.sql("""
                SELECT COALESCE(EXTRACT(EPOCH FROM (NOW() - MIN(created_at)))::bigint, 0)
                FROM webhook_deliveries WHERE status = 'pending'
                """)
                .query(Long.class)
                .single();
    }

    long eventsIngestedTotal() {
        return jdbc.sql("SELECT count(*) FROM events").query(Long.class).single();
    }

    Map<String, Long> lastReceivedEpochSecondsByType() {
        Map<String, Long> lastReceived = new LinkedHashMap<>();
        jdbc.sql("SELECT type, EXTRACT(EPOCH FROM MAX(time))::bigint AS last_received "
                        + "FROM events GROUP BY type ORDER BY type")
                .query((row, rowNumber) -> lastReceived.put(row.getString("type"), row.getLong("last_received")))
                .list();
        return lastReceived;
    }

    Map<String, Long> deliveryAttemptsByResult() {
        Map<String, Long> counts = new LinkedHashMap<>(Map.of("accepted", 0L, "rejected", 0L));
        jdbc.sql("""
                SELECT CASE WHEN status_code BETWEEN 200 AND 299 THEN 'accepted' ELSE 'rejected' END AS result,
                       count(*) AS total
                FROM delivery_attempts GROUP BY result
                """)
                .query((row, rowNumber) -> counts.put(row.getString("result"), row.getLong("total")))
                .list();
        return counts;
    }
}