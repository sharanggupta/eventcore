package dev.eventcore.api;

import dev.eventcore.IntegrationTestBase;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Every error path — domain and framework — returns the one documented shape {"error": ...}. */
class ApiErrorContractTest extends IntegrationTestBase {

    @Test
    void malformedJsonBodyIsARejectedRequest() {
        ResponseEntity<ApiError> response = api().post().uri("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{ not json")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("request body is not valid JSON");
    }

    @Test
    void aNonUuidPathVariableIsARejectedRequest() {
        ResponseEntity<ApiError> response = api().get().uri("/v1/deliveries/not-a-uuid")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("\"id\" has an invalid value");
    }

    @Test
    void anUnknownRouteIsANotFoundInTheSameShape() {
        ResponseEntity<ApiError> response = api().get().uri("/v1/does-not-exist")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error()).isEqualTo("no such route");
    }

    @Test
    void aWrongMethodIsRejectedInTheSameShape() {
        ResponseEntity<ApiError> response = api().put().uri("/v1/deliveries/" + UUID.randomUUID())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().error()).isEqualTo("method not allowed on this route");
    }
}
