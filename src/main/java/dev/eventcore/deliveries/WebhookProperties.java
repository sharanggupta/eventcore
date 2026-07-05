package dev.eventcore.deliveries;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("eventcore.webhooks")
record WebhookProperties(Duration pollInterval, Duration retryBackoff, int maxAttempts) {
}