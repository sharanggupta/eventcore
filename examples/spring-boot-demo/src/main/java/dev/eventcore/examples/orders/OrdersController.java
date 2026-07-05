package dev.eventcore.examples.orders;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** The demo "business": placing an order records an order.placed event in EventCore. */
@RestController
class OrdersController {

    private final EventCoreClient eventCore;

    OrdersController(EventCoreClient eventCore) {
        this.eventCore = eventCore;
    }

    record PlaceOrder(String item) {}

    record OrderPlaced(String orderId, String item) {}

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    OrderPlaced place(@RequestBody PlaceOrder order) {
        String orderId = "ord_" + UUID.randomUUID().toString().substring(0, 8);
        eventCore.publish("order.placed", Map.of("orderId", orderId, "item", order.item()));
        return new OrderPlaced(orderId, order.item());
    }
}
