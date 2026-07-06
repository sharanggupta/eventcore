package dev.eventcore.webhooks;

import dev.eventcore.api.InvalidRequestException;

import java.util.List;

final class PayloadFields {

    /** The wildcard that means "deliver the full payload", on the wire and in the request. */
    static final String ALL = "*";

    private PayloadFields() {
    }

    /** Returns null for "deliver the full payload" (absent, empty, or the {@code ["*"]} wildcard); rejects blank entries. */
    static List<String> normalized(List<String> payloadFields) {
        if (payloadFields == null || payloadFields.isEmpty() || payloadFields.equals(List.of(ALL))) {
            return null;
        }
        if (payloadFields.contains(ALL)) {
            throw new InvalidRequestException("\"*\" means the full payload and cannot be combined with specific fields");
        }
        if (payloadFields.stream().anyMatch(field -> field == null || field.isBlank())) {
            throw new InvalidRequestException("payload fields must not be blank");
        }
        return List.copyOf(payloadFields);
    }

    /** The wire form: the internal null ("full payload") becomes the explicit {@code ["*"]} wildcard. */
    static List<String> wire(List<String> payloadFields) {
        return payloadFields == null ? List.of(ALL) : payloadFields;
    }
}
