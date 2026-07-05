package dev.eventcore.metrics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MetricsController {

    private final PipelineMetrics pipeline;

    MetricsController(PipelineMetrics pipeline) {
        this.pipeline = pipeline;
    }

    @GetMapping(value = "/metrics", produces = "text/plain; version=0.0.4; charset=utf-8")
    String metrics() {
        StringBuilder exposition = new StringBuilder();

        typeHeader(exposition, "eventcore_deliveries", "Webhook deliveries by status");
        pipeline.deliveriesByStatus().forEach((status, count) ->
                labelled(exposition, "eventcore_deliveries", "status", status, count));

        typeHeader(exposition, "eventcore_oldest_pending_delivery_age_seconds",
                "Age of the oldest pending delivery, 0 when none");
        plain(exposition, "eventcore_oldest_pending_delivery_age_seconds",
                pipeline.oldestPendingDeliveryAgeSeconds());

        typeHeader(exposition, "eventcore_events_ingested_total", "Events stored in the log");
        plain(exposition, "eventcore_events_ingested_total", pipeline.eventsIngestedTotal());

        typeHeader(exposition, "eventcore_delivery_attempts_total",
                "Delivery attempts by result (accepted = 2xx)");
        pipeline.deliveryAttemptsByResult().forEach((result, count) ->
                labelled(exposition, "eventcore_delivery_attempts_total", "result", result, count));

        typeHeader(exposition, "eventcore_event_last_received_timestamp_seconds",
                "When each event type last arrived; alert on time() - this > 900");
        pipeline.lastReceivedEpochSecondsByType().forEach((type, epochSeconds) ->
                labelled(exposition, "eventcore_event_last_received_timestamp_seconds", "type", type, epochSeconds));

        return exposition.toString();
    }

    private static void typeHeader(StringBuilder exposition, String metric, String help) {
        exposition.append("# HELP ").append(metric).append(' ').append(help).append('\n')
                .append("# TYPE ").append(metric).append(" gauge\n");
    }

    private static void plain(StringBuilder exposition, String metric, long value) {
        exposition.append(metric).append(' ').append(value).append('\n');
    }

    private static void labelled(StringBuilder exposition, String metric, String label,
                                 String labelValue, long value) {
        exposition.append(metric)
                .append('{').append(label).append("=\"").append(escaped(labelValue)).append("\"} ")
                .append(value).append('\n');
    }

    private static String escaped(String labelValue) {
        return labelValue.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}