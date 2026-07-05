package dev.eventcore.pull;

import java.time.OffsetDateTime;
import java.util.List;

public record PullSubscription(String name, String position, List<String> eventTypes,
                               OffsetDateTime createdAt) {
}
