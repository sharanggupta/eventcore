package dev.eventcore;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

record CreateWebhookRequest(String url) {

    private static final Set<String> DELIVERABLE_SCHEMES = Set.of("http", "https");

    void validate() {
        if (url == null || url.isBlank()) {
            throw new InvalidRequestException("url is required");
        }
        if (!isDeliverable(url)) {
            throw new InvalidRequestException("url must be a valid http(s) URL");
        }
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
