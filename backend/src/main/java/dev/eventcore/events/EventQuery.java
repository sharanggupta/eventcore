package dev.eventcore.events;

import dev.eventcore.api.Cursor;
import dev.eventcore.api.InvalidRequestException;
import dev.eventcore.api.TimeBounds;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

record EventQuery(int limit, Cursor after, String type, OffsetDateTime from, OffsetDateTime to,
                  List<PayloadFilter> payloadFilters) {

    private static final int MAX_LIMIT = 200;

    private static final String PAYLOAD_PARAM_PREFIX = "payload.";

    static EventQuery of(int limit, String cursor, String type, String from, String to,
                         Map<String, String> requestParams) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        return new EventQuery(limit, cursor == null ? null : Cursor.decode(cursor), normalized(type),
                TimeBounds.parsedOrNull(from, "from"), TimeBounds.parsedOrNull(to, "to"),
                payloadFiltersIn(requestParams));
    }

    /** Every payload.&lt;field&gt; request parameter becomes one AND-ed search condition. */
    private static List<PayloadFilter> payloadFiltersIn(Map<String, String> requestParams) {
        return requestParams.entrySet().stream()
                .filter(param -> param.getKey().startsWith(PAYLOAD_PARAM_PREFIX))
                .map(param -> new PayloadFilter(
                        param.getKey().substring(PAYLOAD_PARAM_PREFIX.length()), param.getValue()))
                .toList();
    }

    boolean startsFromTheTop() {
        return after == null;
    }

    boolean filtersByType() {
        return type != null;
    }

    boolean boundedBelow() {
        return from != null;
    }

    boolean boundedAbove() {
        return to != null;
    }

    int rowsToFetch() {
        return limit + 1;
    }

    private static String normalized(String type) {
        return type == null || type.isBlank() ? null : type;
    }
}