package dev.eventcore.webhooks;

import dev.eventcore.api.Wildcards;

import java.util.List;

/**
 * The payload-field projection: absent / empty / {@code ["*"]} mean "the full payload" (null internally).
 * Shares the null↔{@code ["*"]}↔storage mapping with event types via {@link Wildcards}.
 */
final class PayloadFields {

    private PayloadFields() {
    }

    /** Returns null for "deliver the full payload"; rejects the wildcard mixed with specifics, and blanks. */
    static List<String> normalized(List<String> payloadFields) {
        return Wildcards.normalized(payloadFields,
                "\"*\" means the full payload and cannot be combined with specific fields",
                "payload fields must not be blank");
    }

    /** The wire form: null ("full payload") becomes {@code ["*"]}. */
    static List<String> wire(List<String> payloadFields) {
        return Wildcards.wire(payloadFields);
    }
}
