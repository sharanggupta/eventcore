package dev.eventcore;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

record IssuedApiKey(UUID id, String name, String key, OffsetDateTime createdAt) {

    private static final int SECRET_BYTES = 32;

    static IssuedApiKey generate(String name) {
        return new IssuedApiKey(UUID.randomUUID(), name, newSecret(), OffsetDateTime.now());
    }

    private static String newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(secret);
        return "ek_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }
}
