package dev.eventcore.deliveries;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Bound from the eventcore.webhooks prefix (the user-facing name of the feature). */
@ConfigurationProperties("eventcore.webhooks")
record DeliveryProperties(Duration pollInterval, Duration retryBackoff, int maxAttempts) {
}