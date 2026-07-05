/**
 * The event log itself: ingestion ({@code POST /v1/events}), newest-first querying with
 * cursor pagination and type filtering, and the oldest-first readers that pull consumers
 * use. {@link dev.eventcore.events.EventIngestion} is the transactional boundary that
 * appends an event and fans it out to the delivery outbox in one commit.
 */
package dev.eventcore.events;
