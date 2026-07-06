package dev.eventcore.pull;

import dev.eventcore.api.Cursor;
import dev.eventcore.api.InvalidRequestException;
import dev.eventcore.events.EventStore;
import dev.eventcore.events.EventTypes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Pull subscriptions",
        description = "Named durable cursors over the log: pull at your own pace, commit, rewind")
@RestController
@RequestMapping("/v1/pull-subscriptions")
class PullSubscriptionsController {

    private static final int MAX_LIMIT = 200;

    private final PullSubscriptionStore subscriptions;
    private final EventStore events;

    PullSubscriptionsController(PullSubscriptionStore subscriptions, EventStore events) {
        this.subscriptions = subscriptions;
        this.events = events;
    }

    @Operation(summary = "Create a named cursor starting at the beginning, now, or a timestamp")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PullSubscriptionResponse create(@RequestBody CreatePullSubscriptionRequest request) {
        request.validate();
        return PullSubscriptionResponse.of(
                subscriptions.create(request.name(), request.startingPoint(), request.subscribedTypes()));
    }

    @Operation(summary = "Fetch the next batch oldest-first; does not advance the cursor")
    @GetMapping("/{name}/events")
    PullBatch fetch(@PathVariable String name, @RequestParam(defaultValue = "100") int limit) {
        requireValidLimit(limit);
        PullSubscription subscription = subscriptions.one(name);
        return PullBatch.of(events.ascendingAfter(subscription.cursor(), subscription.eventTypes(), limit));
    }

    @Operation(summary = "Advance the cursor to a fetched batch's nextCursor (at-least-once)")
    @PostMapping("/{name}/commit")
    PullSubscriptionResponse commit(@PathVariable String name, @RequestBody CommitRequest request) {
        return PullSubscriptionResponse.of(subscriptions.reposition(name, request.committedPosition()));
    }

    @Operation(summary = "Rewind to the beginning or a timestamp; the consumer replays from there")
    @PostMapping("/{name}/rewind")
    PullSubscriptionResponse rewind(@PathVariable String name, @RequestBody RewindRequest request) {
        return PullSubscriptionResponse.of(subscriptions.reposition(name, request.target()));
    }

    @Operation(summary = "Every consumer's position and lag - who is keeping up, who is stuck")
    @GetMapping
    PullFleet list() {
        return new PullFleet(subscriptions.all().stream().map(this::statusOf).toList());
    }

    private PullSubscriptionStatus statusOf(PullSubscription subscription) {
        Cursor position = subscription.cursor();
        return new PullSubscriptionStatus(
                subscription.name(),
                position == null ? "beginning" : position.time().toString(),
                events.countAfter(position, subscription.eventTypes()),
                EventTypes.wire(subscription.eventTypes()),
                subscription.createdAt());
    }

    private static void requireValidLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
    }
}
