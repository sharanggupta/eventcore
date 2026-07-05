package dev.eventcore.webhooks;

import dev.eventcore.events.EventTypes;

import java.util.List;

record UpdateWebhookFilterRequest(List<String> eventTypes, List<String> payloadFields) {

    /** Null means the subscription receives every event. */
    List<String> subscribedTypes() {
        return EventTypes.normalized(eventTypes);
    }

    /** Null means the full payload is delivered. */
    List<String> deliveredFields() {
        return PayloadFields.normalized(payloadFields);
    }
}