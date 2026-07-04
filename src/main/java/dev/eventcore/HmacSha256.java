package dev.eventcore;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

final class HmacSha256 {

    private HmacSha256() {
    }

    static String hexOf(String secret, String message) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(hmac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException missingFromJvm) {
            throw new IllegalStateException("HmacSHA256 is required but unavailable", missingFromJvm);
        }
    }
}
