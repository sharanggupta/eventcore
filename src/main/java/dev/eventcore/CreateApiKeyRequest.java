package dev.eventcore;

record CreateApiKeyRequest(String name) {

    void validate() {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("name is required");
        }
    }
}
