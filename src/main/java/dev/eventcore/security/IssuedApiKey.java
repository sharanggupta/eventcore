package dev.eventcore.security;

import dev.eventcore.crypto.Secrets;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IssuedApiKey(UUID id, String name, String key, OffsetDateTime createdAt) {

    static IssuedApiKey generate(String name) {
        return new IssuedApiKey(UUID.randomUUID(), name, Secrets.withPrefix("ek_"), OffsetDateTime.now());
    }
}