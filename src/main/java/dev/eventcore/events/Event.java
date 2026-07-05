package dev.eventcore.events;

import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Event(UUID id, OffsetDateTime time, String type, JsonNode payload) {
}