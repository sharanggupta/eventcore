package dev.eventcore.deliveries;

import dev.eventcore.api.InvalidRequestException;

import java.util.UUID;

record BulkRedeliveryRequest(String status, UUID subscriptionId) {

    void validate() {
        if (!"failed".equals(status)) {
            throw new InvalidRequestException("status is required and must be \"failed\"");
        }
    }

    boolean scopedToOneSubscription() {
        return subscriptionId != null;
    }
}