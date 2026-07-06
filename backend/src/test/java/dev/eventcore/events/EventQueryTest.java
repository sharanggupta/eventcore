package dev.eventcore.events;

import dev.eventcore.IntegrationTestBase;
import dev.eventcore.api.ApiError;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventQueryTest extends IntegrationTestBase {

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void startWithAnEmptyEventLog() {
        wipeAllData();
    }

    @Test
    void listingReturnsNewestEventsFirstWithTheirPayloads() {
        OffsetDateTime now = OffsetDateTime.now();
        insertEvent("first.event", now.minusSeconds(2));
        insertEvent("second.event", now.minusSeconds(1));
        insertEvent("third.event", now);

        EventPage page = listEvents("");

        assertThat(page.items()).extracting(Event::type)
                .containsExactly("third.event", "second.event", "first.event");
        assertThat(page.items().getFirst().payload().get("seq").asText()).isEqualTo("third.event");
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void pagesWalkTheFullLogWithoutOverlapOrGaps() {
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < 5; i++) {
            insertEvent("walk.event", now.minusSeconds(i));
        }

        List<UUID> seen = new ArrayList<>();
        String cursor = null;
        do {
            EventPage page = listEvents("?limit=2" + (cursor == null ? "" : "&cursor=" + cursor));
            assertThat(page.items()).hasSizeLessThanOrEqualTo(2);
            page.items().forEach(event -> seen.add(event.id()));
            cursor = page.nextCursor();
        } while (cursor != null);

        assertThat(seen).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    void limitDefaultsToFifty() {
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < 51; i++) {
            insertEvent("bulk.event", now.minusSeconds(i));
        }

        EventPage page = listEvents("");

        assertThat(page.items()).hasSize(50);
        assertThat(page.nextCursor()).isNotNull();
    }

    @Test
    void filteringByTypeReturnsOnlyMatchingEvents() {
        OffsetDateTime now = OffsetDateTime.now();
        insertEvent("order.placed", now.minusSeconds(2));
        insertEvent("user.created", now.minusSeconds(1));
        insertEvent("order.placed", now);

        EventPage page = listEvents("?type=order.placed");

        assertThat(page.items()).hasSize(2)
                .allSatisfy(event -> assertThat(event.type()).isEqualTo("order.placed"));
    }

    @Test
    void typeFilterComposesWithCursorPagination() {
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < 3; i++) {
            insertEvent("wanted.event", now.minusSeconds(i * 2));
            insertEvent("noise.event", now.minusSeconds(i * 2 + 1));
        }

        EventPage firstPage = listEvents("?type=wanted.event&limit=2");
        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.nextCursor()).isNotNull();

        EventPage lastPage = listEvents("?type=wanted.event&limit=2&cursor=" + firstPage.nextCursor());
        assertThat(lastPage.items()).hasSize(1);
        assertThat(lastPage.items().getFirst().type()).isEqualTo("wanted.event");
        assertThat(lastPage.nextCursor()).isNull();
    }

    @Test
    void payloadFieldSearchReturnsOnlyMatchingEvents() {
        OffsetDateTime now = OffsetDateTime.now();
        insertEventWithPayload("user.searched", now.minusSeconds(3), "{\"userId\": \"u_123\", \"plan\": \"pro\"}");
        insertEventWithPayload("user.searched", now.minusSeconds(2), "{\"userId\": \"u_999\"}");
        insertEvent("payload.less", now.minusSeconds(1));

        EventPage page = listEvents("?payload.userId=u_123");

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().payload().get("plan").asText()).isEqualTo("pro");
    }

    @Test
    void payloadSearchMatchesNumbersByTheirTextForm() {
        insertEventWithPayload("invoice.searched", OffsetDateTime.now(), "{\"amountCents\": 4900}");

        EventPage page = listEvents("?payload.amountCents=4900");

        assertThat(page.items()).hasSize(1);
    }

    @Test
    void payloadSearchWalksNestedPathsAndComposesWithOtherFilters() {
        OffsetDateTime now = OffsetDateTime.now();
        insertEventWithPayload("order.searched", now.minusSeconds(2), "{\"order\": {\"id\": \"o1\"}}");
        insertEventWithPayload("noise.searched", now.minusSeconds(1), "{\"order\": {\"id\": \"o1\"}}");

        EventPage page = listEvents("?type=order.searched&payload.order.id=o1");

        assertThat(page.items()).extracting(Event::type).containsExactly("order.searched");
    }

    @Test
    void aBlankPayloadFilterFieldIsRejected() {
        ResponseEntity<ApiError> response = listEventsExpectingRejection("?payload.=x");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("payload filter field must not be blank");
    }

    private void insertEventWithPayload(String type, OffsetDateTime time, String payload) {
        jdbc.sql("INSERT INTO events (id, time, type, payload) VALUES (:id, :time, :type, CAST(:payload AS jsonb))")
                .param("id", UUID.randomUUID())
                .param("time", time)
                .param("type", type)
                .param("payload", payload)
                .update();
    }

    @Test
    void aTimeRangeReturnsOnlyEventsWithinIt() {
        OffsetDateTime now = OffsetDateTime.now();
        insertEvent("too.old", now.minusHours(3));
        insertEvent("in.range", now.minusHours(2));
        insertEvent("too.new", now.minusMinutes(1));

        EventPage page = listEvents("?from=" + now.minusMinutes(150) + "&to=" + now.minusMinutes(60));

        assertThat(page.items()).extracting(Event::type).containsExactly("in.range");
    }

    @Test
    void aTimestampWithoutAnOffsetIsTreatedAsUtc() {
        insertEvent("findable.event", OffsetDateTime.now());

        EventPage page = listEvents("?from=2000-01-01T00:00");

        assertThat(page.items()).extracting(Event::type).contains("findable.event");
    }

    @Test
    void aMalformedTimeBoundIsRejected() {
        ResponseEntity<ApiError> response = listEventsExpectingRejection("?from=yesterday-ish");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("from must be an ISO-8601 timestamp");
    }

    @Test
    void invalidCursorIsRejected() {
        ResponseEntity<ApiError> response = listEventsExpectingRejection("?cursor=not-a-cursor");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("cursor is not valid");
    }

    @Test
    void limitOutsideOneToTwoHundredIsRejected() {
        assertThat(listEventsExpectingRejection("?limit=0").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(listEventsExpectingRejection("?limit=201").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private EventPage listEvents(String query) {
        return api().get().uri("/v1/events" + query).retrieve().body(EventPage.class);
    }

    private ResponseEntity<ApiError> listEventsExpectingRejection(String query) {
        return api().get().uri("/v1/events" + query)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(ApiError.class);
    }

    /** These query tests need a payload to assert on, so override the bare fixture with a sequenced one. */
    @Override
    protected void insertEvent(String type, OffsetDateTime time) {
        jdbc.sql("INSERT INTO events (id, time, type, payload) "
                        + "VALUES (:id, :time, :type, CAST(:payload AS jsonb))")
                .param("id", UUID.randomUUID())
                .param("time", time)
                .param("type", type)
                .param("payload", "{\"seq\": \"" + type + "\"}")
                .update();
    }
}