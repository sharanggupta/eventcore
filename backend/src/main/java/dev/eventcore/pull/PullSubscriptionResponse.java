package dev.eventcore.pull;

import dev.eventcore.events.EventTypes;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/** The create/commit/rewind response: a subscription rendered in wire form (eventTypes as {@code ["*"]} for all). */
public record PullSubscriptionResponse(String name, String position,
                                       @Schema(description = "[\"*\"] means the consumer reads every event type; a specific list restricts it to those types.")
                                       List<String> eventTypes,
                                       OffsetDateTime createdAt) {

    static PullSubscriptionResponse of(PullSubscription subscription) {
        return new PullSubscriptionResponse(subscription.name(), subscription.position(),
                EventTypes.wire(subscription.eventTypes()), subscription.createdAt());
    }
}
