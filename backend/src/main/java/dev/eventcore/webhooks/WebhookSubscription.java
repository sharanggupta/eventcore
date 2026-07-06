package dev.eventcore.webhooks;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** A no-filter subscription omits {@code eventTypes}/{@code payloadFields}: absent means all types, full payload. */
record WebhookSubscription(UUID id, OffsetDateTime createdAt, String url,
                           @JsonInclude(JsonInclude.Include.NON_NULL)
                           @Schema(description = "Absent means every event type is delivered; when present, deliveries are restricted to these types.")
                           List<String> eventTypes,
                           @JsonInclude(JsonInclude.Include.NON_NULL)
                           @Schema(description = "Absent means the full payload is delivered; when present, only these fields are sent.")
                           List<String> payloadFields) {
}
