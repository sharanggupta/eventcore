package dev.eventcore.pull;

import dev.eventcore.api.Cursor;
import dev.eventcore.api.InvalidRequestException;

record CommitRequest(String cursor) {

    Cursor committedPosition() {
        if (cursor == null || cursor.isBlank()) {
            throw new InvalidRequestException("cursor is required");
        }
        return Cursor.decode(cursor);
    }
}
