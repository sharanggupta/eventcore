package dev.eventcore.webhooks;

import dev.eventcore.api.NotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Webhooks", description = "Manage webhook subscriptions; deliveries are HMAC-SHA256 signed")
@RestController
@RequestMapping("/v1/webhooks")
class WebhooksController {

    private final WebhookStore webhooks;

    WebhooksController(WebhookStore webhooks) {
        this.webhooks = webhooks;
    }

    @Operation(summary = "Register a webhook; the signing secret is returned exactly once")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RegisteredWebhook register(@RequestBody CreateWebhookRequest request) {
        request.validate();
        return webhooks.register(request.url(), request.subscribedTypes(), request.deliveredFields());
    }

    @Operation(summary = "Change the eventTypes filter in place; id and secret are kept")
    @PatchMapping("/{id}")
    WebhookSubscription updateFilter(@PathVariable UUID id,
                                     @RequestBody UpdateWebhookFilterRequest request) {
        if (!webhooks.updateFilter(id, request.subscribedTypes())) {
            throw new NotFoundException("webhook subscription not found");
        }
        return webhooks.one(id);
    }

    @Operation(summary = "List subscriptions (never includes secrets)")
    @GetMapping
    List<WebhookSubscription> list() {
        return webhooks.all();
    }

    @Operation(summary = "Remove a subscription and its delivery history")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsubscribe(@PathVariable UUID id) {
        if (!webhooks.remove(id)) {
            throw new NotFoundException("webhook subscription not found");
        }
    }
}