/**
 * The delivery pipeline: a transactional outbox ({@code webhook_deliveries}) drained by a
 * scheduled dispatcher that signs and POSTs each event, records every attempt, and retries
 * with exponential backoff until the per-cycle budget is spent. The HTTP surface lists the
 * outbox, exposes per-attempt histories, and requeues failed deliveries one-at-a-time or in bulk.
 */
package dev.eventcore.deliveries;
