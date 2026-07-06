package dev.eventcore.crypto;

import java.security.SecureRandom;
import java.util.Base64;

public final class Secrets {

    private static final int SECRET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Secrets() {
    }

    public static String withPrefix(String prefix) {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }
}