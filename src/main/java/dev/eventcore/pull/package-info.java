/**
 * Durable pull subscriptions: named cursors over the permanent log. Consumers fetch
 * oldest-first batches at their own pace, commit positions explicitly (at-least-once),
 * rewind to replay history, and are observable as a fleet with per-consumer lag.
 */
package dev.eventcore.pull;
