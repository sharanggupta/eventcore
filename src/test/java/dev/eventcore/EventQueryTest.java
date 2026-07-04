package dev.eventcore;

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
        jdbc.sql("DELETE FROM events").update();
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

    private void insertEvent(String type, OffsetDateTime time) {
        jdbc.sql("INSERT INTO events (id, time, type, payload) "
                        + "VALUES (:id, :time, :type, CAST(:payload AS jsonb))")
                .param("id", UUID.randomUUID())
                .param("time", time)
                .param("type", type)
                .param("payload", "{\"seq\": \"" + type + "\"}")
                .update();
    }
}
