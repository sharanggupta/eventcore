/**
 * Prometheus-text pipeline health at {@code GET /metrics}: delivery states, backlog age,
 * ingest totals, attempt outcomes, and per-type last-received timestamps for
 * flow-stopped alerting.
 *
 * <p>This is a read-only reporting view: {@code PipelineMetrics} queries other features'
 * tables ({@code events}, {@code webhook_deliveries}, {@code delivery_attempts}) directly
 * rather than through their stores — a deliberate shortcut for a single deployable, never
 * a write path.
 */
package dev.eventcore.metrics;
