package dev.eventcore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class Sha256 {

    private Sha256() {
    }

    static String hexOf(String text) {
        return HexFormat.of().formatHex(sha256().digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missingFromJvm) {
            throw new IllegalStateException("SHA-256 is required but unavailable", missingFromJvm);
        }
    }
}
