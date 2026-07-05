package dev.eventcore;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record DeliveryDetail(UUID id, UUID eventId, UUID subscriptionId, String status, int attempts,
                      OffsetDateTime createdAt, List<DeliveryAttempt> deliveryAttempts) {

    static DeliveryDetail of(Delivery delivery, List<DeliveryAttempt> deliveryAttempts) {
        return new DeliveryDetail(delivery.id(), delivery.eventId(), delivery.subscriptionId(),
                delivery.status(), delivery.attempts(), delivery.createdAt(), deliveryAttempts);
    }
}
