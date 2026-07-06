package dev.eventcore.pull;

import dev.eventcore.IntegrationTestBase;
import dev.eventcore.api.ApiError;
import dev.eventcore.events.Event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PullSubscriptionsTest extends IntegrationTestBase {

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void startClean() {
        wipeAllData();
    }

    @Test
    void aNewConsumerReadsHistoryFromTheBeginningWithoutAdvancing() {
        OffsetDateTime now = OffsetDateTime.now();
        insertEvent("first.event", now.minusSeconds(30));
        insertEvent("second.event", now.minusSeconds(20));
        insertEvent("third.event", now.minusSeconds(10));

        PullSubscription created = createSubscription("""
                {"name": "fraud-lens", "from": "beginning"}
                """);
        assertThat(created.position()).isNull();

        PullBatch firstPeek = fetch("fraud-lens", 2);
        assertThat(firstPeek.items()).extracting(Event::type)
                .containsExactly("first.event", "second.event");
        assertThat(firstPeek.nextCursor()).isNotNull();

        PullBatch secondPeek = fetch("fraud-lens", 2);
        assertThat(secondPeek.items()).extracting(Event::type)
                .containsExactly("first.event", "second.event");
    }

    @Test
    void aCommitLoopDrainsTheLogExactlyOnce() {
        OffsetDateTime now = OffsetDateTime.now();
        insertEvent("drain.1", now.minusSeconds(30));
        insertEvent("drain.2", now.minusSeconds(20));
        insertEvent("drain.3", now.minusSeconds(10));
        createSubscription("""
                {"name": "drainer", "from": "beginning"}
                """);

        int seen = 0;
        PullBatch batch = fetch("drainer", 2);
        while (!batch.items().isEmpty()) {
            seen += batch.items().size();
            PullSubscription committed = commit("drainer", batch.nextCursor());
            assertThat(committed.position()).isEqualTo(batch.nextCursor());
            batch = fetch("drainer", 2);
        }

        assertThat(seen).isEqualTo(3);
        assertThat(batch.nextCursor()).isNull();
    }

    @Test
    void startingFromNowSkipsHistory() {
        insertEvent("ancient.event", OffsetDateTime.now().minusSeconds(60));
        createSubscription("""
                {"name": "live-only", "from": "now"}
                """);

        assertThat(fetch("live-only", 10).items()).isEmpty();

        postEvent("fresh.event");
        assertThat(fetch("live-only", 10).items())
                .extracting(Event::type).containsExactly("fresh.event");
    }

    @Test
    void startingFromATimestampStartsExactlyThere() {
        OffsetDateTime now = OffsetDateTime.now();
        insertEvent("too.old", now.minusSeconds(100));
        insertEvent("wanted", now.minusSeconds(10));

        createSubscription("{\"name\": \"since\", \"from\": \"" + now.minusSeconds(50) + "\"}");

        assertThat(fetch("since", 10).items())
                .extracting(Event::type).containsExactly("wanted");
    }

    @Test
    void aConsumerWithATypeFilterSeesOnlyThoseTypes() {
        OffsetDateTime now = OffsetDateTime.now();
        insertEvent("invoice.paid", now.minusSeconds(30));
        insertEvent("noise.event", now.minusSeconds(20));
        insertEvent("refund.issued", now.minusSeconds(10));

        createSubscription("""
                {"name": "finance", "from": "beginning", "eventTypes": ["invoice.paid", "refund.issued"]}
                """);

        assertThat(fetch("finance", 10).items())
                .extracting(Event::type).containsExactly("invoice.paid", "refund.issued");
    }

    @Test
    void rewindingToTheBeginningReplaysEverything() {
        OffsetDateTime now = OffsetDateTime.now();
        insertEvent("replay.1", now.minusSeconds(20));
        insertEvent("replay.2", now.minusSeconds(10));
        createSubscription("{\"name\": \"repairer\", \"from\": \"beginning\"}");
        PullBatch drained = fetch("repairer", 10);
        commit("repairer", drained.nextCursor());
        assertThat(fetch("repairer", 10).items()).isEmpty();

        PullSubscription rewound = rewind("repairer", "{\"to\": \"beginning\"}");

        assertThat(rewound.position()).isNull();
        assertThat(fetch("repairer", 10).items())
                .extracting(Event::type).containsExactly("replay.1", "replay.2");
    }

    @Test
    void rewindingToATimestampReplaysFromThere() {
        OffsetDateTime now = OffsetDateTime.now();
        insertEvent("before.bug", now.minusSeconds(100));
        insertEvent("since.bug", now.minusSeconds(10));
        createSubscription("{\"name\": \"surgeon\", \"from\": \"beginning\"}");
        commit("surgeon", fetch("surgeon", 10).nextCursor());

        rewind("surgeon", "{\"to\": \"" + now.minusSeconds(50) + "\"}");

        assertThat(fetch("surgeon", 10).items())
                .extracting(Event::type).containsExactly("since.bug");
    }

    @Test
    void aMalformedRewindTargetIsRejected() {
        createSubscription("{\"name\": \"careful\"}");

        var response = api().post().uri("/v1/pull-subscriptions/careful/rewind")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"to\": \"last-tuesday\"}")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error())
                .isEqualTo("to must be \"beginning\" or an ISO-8601 timestamp");
    }

    @Test
    void theFleetViewShowsEveryConsumersPositionAndLag() {
        OffsetDateTime now = OffsetDateTime.now();
        insertEvent("fleet.a", now.minusSeconds(30));
        insertEvent("fleet.b", now.minusSeconds(20));
        insertEvent("noise.z", now.minusSeconds(10));
        createSubscription("{\"name\": \"backfiller\", \"from\": \"beginning\"}");
        createSubscription("{\"name\": \"live-tail\", \"from\": \"now\"}");
        createSubscription("""
                {"name": "picky", "from": "beginning", "eventTypes": ["fleet.a", "fleet.b"]}
                """);
        commit("backfiller", fetch("backfiller", 1).nextCursor());

        PullFleet fleet = api().get().uri("/v1/pull-subscriptions")
                .retrieve().body(PullFleet.class);

        assertThat(fleet.items()).extracting(PullSubscriptionStatus::name)
                .containsExactlyInAnyOrder("backfiller", "live-tail", "picky");
        PullSubscriptionStatus backfiller = statusOf(fleet, "backfiller");
        assertThat(backfiller.lagEvents()).isEqualTo(2);
        assertThat(backfiller.position()).isNotEqualTo("beginning");
        assertThat(OffsetDateTime.parse(backfiller.position())).isNotNull();
        assertThat(statusOf(fleet, "picky").position()).isEqualTo("beginning");
        assertThat(statusOf(fleet, "live-tail").lagEvents()).isZero();
        assertThat(statusOf(fleet, "picky").lagEvents()).isEqualTo(2);
    }

    private PullSubscriptionStatus statusOf(PullFleet fleet, String name) {
        return fleet.items().stream().filter(status -> status.name().equals(name))
                .findFirst().orElseThrow();
    }

    private PullSubscription rewind(String name, String body) {
        return api().post().uri("/v1/pull-subscriptions/" + name + "/rewind")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(PullSubscription.class);
    }

    @Test
    void aDuplicateNameIs409() {
        createSubscription("{\"name\": \"twice\"}");

        ResponseEntity<ApiError> response = createExpectingError("{\"name\": \"twice\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error())
                .isEqualTo("a pull subscription named \"twice\" already exists");
    }

    @Test
    void aBlankNameIsRejected() {
        ResponseEntity<ApiError> response = createExpectingError("{\"name\": \"  \"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("name is required");
    }

    @Test
    void aMalformedStartingPointIsRejected() {
        ResponseEntity<ApiError> response = createExpectingError("{\"name\": \"x\", \"from\": \"yesterday-ish\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error())
                .isEqualTo("from must be \"beginning\", \"now\", or an ISO-8601 timestamp");
    }

    @Test
    void anUnknownConsumerIs404() {
        var fetchResponse = api().get().uri("/v1/pull-subscriptions/ghost/events")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(ApiError.class);

        assertThat(fetchResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(fetchResponse.getBody().error()).isEqualTo("pull subscription not found");
    }

    @Test
    void anInvalidCommitCursorIsRejected() {
        createSubscription("{\"name\": \"strict\"}");

        var response = api().post().uri("/v1/pull-subscriptions/strict/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"cursor\": \"garbage\"}")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private PullSubscription createSubscription(String body) {
        return api().post().uri("/v1/pull-subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(PullSubscription.class);
    }

    private ResponseEntity<ApiError> createExpectingError(String body) {
        return api().post().uri("/v1/pull-subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(ApiError.class);
    }

    private PullBatch fetch(String name, int limit) {
        return api().get().uri("/v1/pull-subscriptions/" + name + "/events?limit=" + limit)
                .retrieve()
                .body(PullBatch.class);
    }

    private PullSubscription commit(String name, String cursor) {
        return api().post().uri("/v1/pull-subscriptions/" + name + "/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"cursor\": \"" + cursor + "\"}")
                .retrieve()
                .body(PullSubscription.class);
    }

    private void postEvent(String type) {
        api().post().uri("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"type\": \"" + type + "\"}")
                .retrieve()
                .toBodilessEntity();
    }

    private void insertEvent(String type, OffsetDateTime time) {
        jdbc.sql("INSERT INTO events (id, time, type) VALUES (:id, :time, :type)")
                .param("id", UUID.randomUUID())
                .param("time", time)
                .param("type", type)
                .update();
    }
}
