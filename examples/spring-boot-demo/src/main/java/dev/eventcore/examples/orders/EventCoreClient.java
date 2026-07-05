package dev.eventcore.examples.orders;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Everything an application needs to publish events to EventCore: one POST. */
@Component
class EventCoreClient {

    private final RestClient eventCore;

    EventCoreClient(@Value("${eventcore.url}") String url,
                    @Value("${eventcore.api-key}") String apiKey) {
        this.eventCore = RestClient.builder()
                .baseUrl(url)
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    void publish(String type, Map<String, Object> payload) {
        eventCore.post()
                .uri("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", type, "payload", payload))
                .retrieve()
                .toBodilessEntity();
    }
}
