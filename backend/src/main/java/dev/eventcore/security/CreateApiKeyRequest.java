package dev.eventcore.security;

import dev.eventcore.api.InvalidRequestException;

record CreateApiKeyRequest(String name) {

    void validate() {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("name is required");
        }
    }
}