package dev.eventcore.security;

import dev.eventcore.api.NotFoundException;
import dev.eventcore.api.UnauthorizedException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "API keys", description = "Admin-token-guarded key management; only SHA-256 hashes are stored")
@RestController
@RequestMapping(ApiKeysController.BASE_PATH)
class ApiKeysController {

    /** The one place this route is spelled; the auth filter reads it to decide what X-API-Key does not guard. */
    static final String BASE_PATH = "/v1/api-keys";

    private final ApiKeyStore apiKeys;
    private final SecurityProperties security;

    ApiKeysController(ApiKeyStore apiKeys, SecurityProperties security) {
        this.apiKeys = apiKeys;
        this.security = security;
    }

@Operation(summary = "Issue a key; the plaintext appears only in this response")
    @PostMapping    @ResponseStatus(HttpStatus.CREATED)
    IssuedApiKey issue(@RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
                       @RequestBody CreateApiKeyRequest request) {
        requireAdmin(adminToken);
        request.validate();
        return apiKeys.issue(request.name());
    }

@Operation(summary = "Revoke a key immediately; the record is kept for audit")
    @DeleteMapping("/{id}")    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
                @PathVariable UUID id) {
        requireAdmin(adminToken);
        if (!apiKeys.revoke(id)) {
            throw new NotFoundException("api key not found");
        }
    }

    private void requireAdmin(String presentedToken) {
        if (!security.admits(presentedToken)) {
            throw new UnauthorizedException("a valid X-Admin-Token header is required");
        }
    }
}