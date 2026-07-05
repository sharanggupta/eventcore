package dev.eventcore;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/deliveries")
class DeliveriesController {

    private final DeliveryOutbox deliveries;

    DeliveriesController(DeliveryOutbox deliveries) {
        this.deliveries = deliveries;
    }

    @GetMapping
    DeliveryPage list(@RequestParam(defaultValue = "50") int limit,
                      @RequestParam(required = false) String cursor,
                      @RequestParam(required = false) String status) {
        return deliveries.page(DeliveryQuery.of(limit, cursor, status));
    }
}
