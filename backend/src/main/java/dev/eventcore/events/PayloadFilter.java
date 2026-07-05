package dev.eventcore.events;

import dev.eventcore.api.InvalidRequestException;

/**
 * One payload.&lt;field&gt;=&lt;value&gt; search condition. Dotted fields walk into
 * nested objects; values compare against the JSON value's text form, so
 * strings and numbers both match the way a curl user expects.
 */
record PayloadFilter(String field, String value) {

    PayloadFilter {
        if (field == null || field.isBlank()) {
            throw new InvalidRequestException("payload filter field must not be blank");
        }
    }

    /** {@code order.id} becomes the Postgres path literal {@code {order,id}}. */
    String pathLiteral() {
        return "{" + field.replace('.', ',') + "}";
    }
}
