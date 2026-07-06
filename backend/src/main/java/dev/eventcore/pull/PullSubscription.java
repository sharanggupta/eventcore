package dev.eventcore.pull;

import dev.eventcore.api.Cursor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The internal read model for a durable consumer: {@code eventTypes} is null for "every type"
 * and {@code position} is the encoded cursor (null at the beginning of the log). It is not a wire
 * contract — {@link PullSubscriptionResponse} is what create/commit/rewind return.
 */
record PullSubscription(String name, String position, List<String> eventTypes, OffsetDateTime createdAt) {

    /** The decoded log position, or null at the beginning. Owns the decode so callers don't repeat it. */
    Cursor cursor() {
        return position == null ? null : Cursor.decode(position);
    }
}
