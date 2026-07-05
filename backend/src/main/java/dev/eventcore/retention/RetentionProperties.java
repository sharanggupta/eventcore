package dev.eventcore.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** A zero duration means "keep forever" — the default for self-hosted instances. */
@ConfigurationProperties("eventcore.retention")
public record RetentionProperties(Duration eventsMaxAge, Duration deliveryHistoryMaxAge) {

    boolean eventsExpire() {
        return expires(eventsMaxAge);
    }

    boolean deliveryHistoryExpires() {
        return expires(deliveryHistoryMaxAge);
    }

    private static boolean expires(Duration maxAge) {
        return maxAge != null && !maxAge.isZero();
    }
}
