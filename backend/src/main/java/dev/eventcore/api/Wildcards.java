package dev.eventcore.api;

import java.util.List;

/**
 * The one owner of the "all, or this explicit list" filter vocabulary shared by event-type routing
 * and payload-field projection. "All" is a single concept with three encodings, and this class is
 * where they meet: absent / empty / {@code ["*"]} on the request, {@code null} in the domain and the
 * database column (so matching stays {@code IS NULL OR ...}), and {@code ["*"]} on the wire.
 */
public final class Wildcards {

    /** The wildcard element that means "everything", on the request and on the wire. */
    public static final String ALL = "*";

    private Wildcards() {
    }

    /**
     * The domain form: absent, empty, or the {@code ["*"]} wildcard all become null ("everything").
     * The wildcard cannot be mixed with specific values, and blanks are rejected — callers pass the
     * messages their own vocabulary uses.
     */
    public static List<String> normalized(List<String> raw, String wildcardMeaning, String blankMessage) {
        if (raw == null || raw.isEmpty() || raw.equals(List.of(ALL))) {
            return null;
        }
        if (raw.contains(ALL)) {
            throw new InvalidRequestException(wildcardMeaning);
        }
        if (raw.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new InvalidRequestException(blankMessage);
        }
        return List.copyOf(raw);
    }

    /** The wire form: the domain null ("everything") becomes the explicit {@code ["*"]} wildcard. */
    public static List<String> wire(List<String> normalized) {
        return normalized == null ? List.of(ALL) : normalized;
    }
}
