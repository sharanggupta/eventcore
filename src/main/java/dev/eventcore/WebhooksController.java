package dev.eventcore;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/webhooks")
class WebhooksController {

    private final WebhookStore webhooks;

    WebhooksController(WebhookStore webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WebhookSubscription register(@RequestBody CreateWebhookRequest request) {
        request.validate();
        return webhooks.register(request.url());
    }

    @GetMapping
    List<WebhookSubscription> list() {
        return webhooks.all();
    }
}
