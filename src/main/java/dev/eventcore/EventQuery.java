package dev.eventcore;

record EventQuery(int limit, Cursor after) {

    private static final int MAX_LIMIT = 200;

    static EventQuery of(int limit, String cursor) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        return new EventQuery(limit, cursor == null ? null : Cursor.decode(cursor));
    }

    boolean startsFromTheTop() {
        return after == null;
    }

    int rowsToFetch() {
        return limit + 1;
    }
}
