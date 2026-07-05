package dev.eventcore.events;

import dev.eventcore.api.InvalidRequestException;

import tools.jackson.databind.JsonNode;

record CreateEventRequest(String type, JsonNode payload) {

    void validate() {
        if (type == null || type.isBlank()) {
            throw new InvalidRequestException("type is required");
        }
    }
}