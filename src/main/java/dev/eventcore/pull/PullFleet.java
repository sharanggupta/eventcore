package dev.eventcore.pull;

import java.util.List;

public record PullFleet(List<PullSubscriptionStatus> items) {
}
