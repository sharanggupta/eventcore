package dev.eventcore;

import tools.jackson.databind.JsonNode;

record CreateEventRequest(String type, JsonNode payload) {

    void validate() {
        if (type == null || type.isBlank()) {
            throw new InvalidRequestException("type is required");
        }
    }
}
