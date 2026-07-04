package dev.eventcore;

import java.security.SecureRandom;
import java.util.Base64;

final class Secrets {

    private static final int SECRET_BYTES = 32;

    private Secrets() {
    }

    static String withPrefix(String prefix) {
        byte[] secret = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(secret);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }
}
