package dev.eventcore;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyAuthenticationTest extends IntegrationTestBase {

    @Test
    void eventEndpointsRequireAnApiKey() {
        assertThat(anonymousGet("/v1/events").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymousPost("/v1/events", "{\"type\": \"sneaky\"}").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void webhookEndpointsRequireAnApiKey() {
        assertThat(anonymousGet("/v1/webhooks").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anUnknownApiKeyIsRejectedWithAClearMessage() {
        ResponseEntity<ApiError> response = anonymousApi().get()
                .uri("/v1/events")
                .header("X-API-Key", "ek_not-a-real-key")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().error()).isEqualTo("a valid X-API-Key header is required");
    }

    @Test
    void aRecognizedApiKeyIsAdmitted() {
        var response = api().get().uri("/v1/events").retrieve().toEntity(EventPage.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void healthNeedsNoApiKey() {
        String response = anonymousApi().get().uri("/health").retrieve().body(String.class);

        assertThat(response).isEqualTo("OK");
    }

    @Test
    void issuingApiKeysNeedsTheAdminTokenNotAnApiKey() {
        var response = anonymousApi().post()
                .uri("/v1/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Admin-Token", ADMIN_TOKEN)
                .body("{\"name\": \"issued-without-api-key\"}")
                .retrieve()
                .toEntity(IssuedApiKey.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<ApiError> anonymousGet(String path) {
        return anonymousApi().get().uri(path)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(ApiError.class);
    }

    private ResponseEntity<ApiError> anonymousPost(String path, String body) {
        return anonymousApi().post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(ApiError.class);
    }
}
