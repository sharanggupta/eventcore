package dev.eventcore.webhooks;

import java.util.List;

record UpdateWebhookFilterRequest(List<String> eventTypes) {

    /** Null means the subscription receives every event. */
    List<String> subscribedTypes() {
        return EventTypes.normalized(eventTypes);
    }
}