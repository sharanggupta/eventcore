package dev.eventcore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyIssuanceTest extends IntegrationTestBase {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void issuingAKeyReturnsThePlaintextExactlyOnce() {
        var response = issueKey(ADMIN_TOKEN, """
                {"name": "ci-pipeline"}
                """).toEntity(IssuedApiKey.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        IssuedApiKey issued = response.getBody();
        assertThat(issued.id()).isNotNull();
        assertThat(issued.createdAt()).isNotNull();
        assertThat(issued.name()).isEqualTo("ci-pipeline");
        assertThat(issued.key()).startsWith("ek_");
    }

    @Test
    void onlyTheSha256HashOfTheKeyIsStored() throws NoSuchAlgorithmException {
        IssuedApiKey issued = issueKey(ADMIN_TOKEN, """
                {"name": "hash-check"}
                """).body(IssuedApiKey.class);

        String stored = storedHashOf(issued.id());
        assertThat(stored).isNotEqualTo(issued.key());
        assertThat(stored).isEqualTo(sha256HexOf(issued.key()));
    }

    @Test
    void aMissingAdminTokenIsRejected() {
        ResponseEntity<ApiError> response = issueKeyExpectingRejection(null, """
                {"name": "sneaky"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().error()).isEqualTo("a valid X-Admin-Token header is required");
    }

    @Test
    void aWrongAdminTokenIsRejected() {
        ResponseEntity<ApiError> response = issueKeyExpectingRejection("not-the-token", """
                {"name": "sneaky"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aMissingNameIsRejected() {
        ResponseEntity<ApiError> response = issueKeyExpectingRejection(ADMIN_TOKEN, "{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("name is required");
    }

    private RestClient.ResponseSpec issueKey(String adminToken, String body) {
        var request = api().post()
                .uri("/v1/api-keys")
                .contentType(MediaType.APPLICATION_JSON);
        if (adminToken != null) {
            request = request.header("X-Admin-Token", adminToken);
        }
        return request.body(body).retrieve();
    }

    private ResponseEntity<ApiError> issueKeyExpectingRejection(String adminToken, String body) {
        return issueKey(adminToken, body)
                .onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(ApiError.class);
    }

    private String storedHashOf(UUID id) {
        return jdbc.sql("SELECT key_hash FROM api_keys WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }

    private String sha256HexOf(String text) throws NoSuchAlgorithmException {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(sha256.digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
