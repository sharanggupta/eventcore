package dev.eventcore;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
class WebhookStore {

    private final JdbcClient jdbc;

    WebhookStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    WebhookSubscription register(String url) {
        WebhookSubscription subscription = WebhookSubscription.now(url);
        jdbc.sql("INSERT INTO webhook_subscriptions (id, created_at, url) VALUES (:id, :createdAt, :url)")
                .param("id", subscription.id())
                .param("createdAt", subscription.createdAt())
                .param("url", subscription.url())
                .update();
        return subscription;
    }

    boolean remove(UUID id) {
        int removed = jdbc.sql("DELETE FROM webhook_subscriptions WHERE id = :id")
                .param("id", id)
                .update();
        return removed > 0;
    }

    List<WebhookSubscription> all() {
        return jdbc.sql("SELECT id, created_at, url FROM webhook_subscriptions ORDER BY created_at")
                .query(this::toSubscription)
                .list();
    }

    private WebhookSubscription toSubscription(ResultSet row, int rowNumber) throws SQLException {
        return new WebhookSubscription(
                row.getObject("id", UUID.class),
                row.getObject("created_at", OffsetDateTime.class),
                row.getString("url"));
    }
}
