package dev.eventcore.pull;

import java.time.OffsetDateTime;
import java.util.List;

public record PullSubscriptionStatus(String name, String position, OffsetDateTime positionTime,
                                     long lagEvents, List<String> eventTypes,
                                     OffsetDateTime createdAt) {
}
