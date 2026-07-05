package dev.eventcore.webhooks;

import dev.eventcore.api.InvalidRequestException;
import dev.eventcore.events.EventTypes;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

record CreateWebhookRequest(String url, List<String> eventTypes) {

    private static final Set<String> DELIVERABLE_SCHEMES = Set.of("http", "https");

    void validate() {
        if (url == null || url.isBlank()) {
            throw new InvalidRequestException("url is required");
        }
        if (!isDeliverable(url)) {
            throw new InvalidRequestException("url must be a valid http(s) URL");
        }
        subscribedTypes();
    }

    /** Null means the subscription receives every event. */
    List<String> subscribedTypes() {
        return EventTypes.normalized(eventTypes);
    }

    private static boolean isDeliverable(String url) {
        try {
            URI uri = new URI(url);
            return DELIVERABLE_SCHEMES.contains(uri.getScheme()) && uri.getHost() != null;
        } catch (URISyntaxException malformed) {
            return false;
        }
    }
}