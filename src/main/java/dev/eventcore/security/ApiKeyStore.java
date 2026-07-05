package dev.eventcore.security;

import dev.eventcore.crypto.Sha256;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ApiKeyStore {

    private final JdbcClient jdbc;

    ApiKeyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public IssuedApiKey issue(String name) {
        IssuedApiKey issued = IssuedApiKey.generate(name);
        jdbc.sql("INSERT INTO api_keys (id, name, key_hash, created_at) "
                        + "VALUES (:id, :name, :keyHash, :createdAt)")
                .param("id", issued.id())
                .param("name", issued.name())
                .param("keyHash", Sha256.hexOf(issued.key()))
                .param("createdAt", issued.createdAt())
                .update();
        return issued;
    }

    boolean recognizes(String key) {
        return jdbc.sql("SELECT count(*) FROM api_keys WHERE key_hash = :keyHash AND revoked_at IS NULL")
                .param("keyHash", Sha256.hexOf(key))
                .query(Long.class)
                .single() > 0;
    }

    boolean revoke(UUID id) {
        int revoked = jdbc.sql("UPDATE api_keys SET revoked_at = NOW() "
                        + "WHERE id = :id AND revoked_at IS NULL")
                .param("id", id)
                .update();
        return revoked > 0;
    }
}