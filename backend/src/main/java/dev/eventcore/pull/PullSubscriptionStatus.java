package dev.eventcore.pull;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/** A no-filter consumer omits {@code eventTypes}: absent means it reads every event type. */
public record PullSubscriptionStatus(String name, String position, OffsetDateTime positionTime,
                                     long lagEvents,
                                     @JsonInclude(JsonInclude.Include.NON_NULL)
                                     @Schema(description = "Absent means the consumer reads every event type; when present, it reads only these types.")
                                     List<String> eventTypes,
                                     OffsetDateTime createdAt) {
}
