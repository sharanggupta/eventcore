package dev.eventcore.deliveries;

import dev.eventcore.api.NotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

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

    @GetMapping("/{id}")
    DeliveryDetail detail(@PathVariable UUID id) {
        return deliveries.find(id)
                .orElseThrow(() -> new NotFoundException("delivery not found"));
    }

    @PostMapping("/{id}/redeliver")
    @ResponseStatus(HttpStatus.ACCEPTED)
    RedeliveryReceipt redeliver(@PathVariable UUID id) {
        return deliveries.requeue(id);
    }

    @PostMapping("/redeliver")
    @ResponseStatus(HttpStatus.ACCEPTED)
    RedeliveredBatch redeliverAll(@RequestBody BulkRedeliveryRequest request) {
        request.validate();
        return deliveries.requeueAllFailed(request);
    }
}