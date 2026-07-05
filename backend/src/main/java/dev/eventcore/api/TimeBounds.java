package dev.eventcore.api;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** Parses optional from/to request parameters; null means "unbounded". */
public final class TimeBounds {

    private TimeBounds() {
    }

    public static OffsetDateTime parsedOrNull(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        // An unencoded "+01:00" offset arrives as " 01:00" after URL decoding;
        // ISO-8601 never contains spaces, so restoring the plus is always safe.
        String candidate = value.replace(' ', '+');
        try {
            return OffsetDateTime.parse(candidate);
        } catch (DateTimeParseException withoutOffset) {
            try {
                return LocalDateTime.parse(candidate).atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException malformed) {
                throw new InvalidRequestException(parameterName + " must be an ISO-8601 timestamp");
            }
        }
    }
}
