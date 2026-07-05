package dev.eventcore.retention;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class RetentionSweeper {

    private final JdbcClient jdbc;
    private final RetentionProperties retention;

    RetentionSweeper(JdbcClient jdbc, RetentionProperties retention) {
        this.jdbc = jdbc;
        this.retention = retention;
    }

    @Scheduled(cron = "${eventcore.retention.sweep-cron:0 17 3 * * *}")
    void sweepOnSchedule() {
        sweepNow();
    }

    public void sweepNow() {
        if (retention.eventsExpire()) {
            dropEventChunksBefore(OffsetDateTime.now().minus(retention.eventsMaxAge()));
        }
        if (retention.deliveryHistoryExpires()) {
            deleteDeliveryHistoryBefore(OffsetDateTime.now().minus(retention.deliveryHistoryMaxAge()));
        }
    }

    /** Whole TimescaleDB chunks only — dropping a chunk is instant and reclaims disk immediately. */
    private void dropEventChunksBefore(OffsetDateTime cutoff) {
        jdbc.sql("SELECT drop_chunks('events', older_than => CAST(:cutoff AS timestamptz))")
                .param("cutoff", cutoff)
                .query(String.class)
                .list();
    }

    private void deleteDeliveryHistoryBefore(OffsetDateTime cutoff) {
        jdbc.sql("DELETE FROM webhook_deliveries WHERE created_at < :cutoff")
                .param("cutoff", cutoff)
                .update();
    }
}
