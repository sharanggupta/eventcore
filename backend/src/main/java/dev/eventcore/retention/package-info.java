/**
 * Retention policies: optional, per-instance rotation of old data so a durable
 * log stays inside a finite disk. Disabled by default — self-hosted EventCore
 * keeps everything forever unless configured otherwise. Events drop by whole
 * TimescaleDB chunks; delivery history deletes by row (attempts cascade).
 */
package dev.eventcore.retention;
