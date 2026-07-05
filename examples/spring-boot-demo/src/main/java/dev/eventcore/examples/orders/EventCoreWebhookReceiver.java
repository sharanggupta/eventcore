package dev.eventcore.examples.orders;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Receives EventCore webhook deliveries. The one rule of webhook receiving:
 * verify the signature BEFORE trusting the body - only EventCore knows the secret.
 */
@RestController
class EventCoreWebhookReceiver {

    private final String webhookSecret;

    EventCoreWebhookReceiver(@Value("${eventcore.webhook-secret}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhooks/eventcore")
    ResponseEntity<Void> receive(@RequestHeader("X-EventCore-Signature") String signature,
                                 @RequestBody String body) {
        if (!signatureMatches(signature, body)) {
            System.out.println("REJECTED delivery: signature mismatch");
            return ResponseEntity.status(401).build();
        }
        System.out.println("verified delivery: " + body);
        return ResponseEntity.ok().build();
    }

    private boolean signatureMatches(String received, String body) {
        String expected = "sha256=" + hmacSha256Hex(webhookSecret, body);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacSha256Hex(String secret, String message) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(hmac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("HmacSHA256 is required", unavailable);
        }
    }
}
