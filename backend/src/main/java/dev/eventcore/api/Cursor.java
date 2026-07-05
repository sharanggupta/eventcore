package dev.eventcore.api;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

public record Cursor(OffsetDateTime time, UUID id) {

    public static Cursor decode(String encoded) {
        try {
            String[] parts = decodeToText(encoded).split("\\|");
            return new Cursor(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException malformed) {
            throw new InvalidRequestException("cursor is not valid");
        }
    }

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((time + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeToText(String encoded) {
        return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}