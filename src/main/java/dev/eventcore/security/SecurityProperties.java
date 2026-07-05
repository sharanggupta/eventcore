package dev.eventcore.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@ConfigurationProperties("eventcore.security")
record SecurityProperties(String adminToken) {

    boolean admits(String presentedToken) {
        return isConfigured() && presentedToken != null && matches(presentedToken);
    }

    private boolean isConfigured() {
        return adminToken != null && !adminToken.isBlank();
    }

    private boolean matches(String presentedToken) {
        return MessageDigest.isEqual(
                adminToken.getBytes(StandardCharsets.UTF_8),
                presentedToken.getBytes(StandardCharsets.UTF_8));
    }
}