/**
 * EventCore: a self-hosted event audit log with reliable, signed webhook delivery.
 *
 * <p>Each subpackage is one capability; inside each, {@code *Controller} is the HTTP
 * surface, {@code *Store} owns the SQL, and records are the API contracts. Start at
 * {@link dev.eventcore.events} and follow an event through {@link dev.eventcore.deliveries}.
 */
package dev.eventcore;
