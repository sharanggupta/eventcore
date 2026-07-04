package dev.eventcore;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ApiKeyStore {

    private final JdbcClient jdbc;

    ApiKeyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    IssuedApiKey issue(String name) {
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
        return jdbc.sql("SELECT count(*) FROM api_keys WHERE key_hash = :keyHash")
                .param("keyHash", Sha256.hexOf(key))
                .query(Long.class)
                .single() > 0;
    }
}
