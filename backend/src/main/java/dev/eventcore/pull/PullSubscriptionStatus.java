package dev.eventcore.pull;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/** One consumer in the fleet view; {@code position} is "beginning" or the ISO-8601 timestamp it has reached. */
public record PullSubscriptionStatus(String name,
                                     @Schema(description = "Where the consumer sits in the log: \"beginning\", or the ISO-8601 timestamp it has committed up to. Mirrors the vocabulary of the create/rewind \"from\"/\"to\".")
                                     String position,
                                     long lagEvents,
                                     @Schema(description = "[\"*\"] means the consumer reads every event type; a specific list restricts it to those types.")
                                     List<String> eventTypes,
                                     OffsetDateTime createdAt) {
}
