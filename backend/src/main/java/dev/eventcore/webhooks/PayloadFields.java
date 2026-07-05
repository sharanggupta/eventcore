package dev.eventcore.webhooks;

import dev.eventcore.api.InvalidRequestException;

import java.util.List;

final class PayloadFields {

    private PayloadFields() {
    }

    /** Returns null for "deliver the full payload"; rejects blank entries. */
    static List<String> normalized(List<String> payloadFields) {
        if (payloadFields == null || payloadFields.isEmpty()) {
            return null;
        }
        if (payloadFields.stream().anyMatch(field -> field == null || field.isBlank())) {
            throw new InvalidRequestException("payload fields must not be blank");
        }
        return List.copyOf(payloadFields);
    }
}
