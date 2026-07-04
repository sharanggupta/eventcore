package dev.eventcore;

import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

record Event(UUID id, OffsetDateTime time, String type, JsonNode payload) {
}
