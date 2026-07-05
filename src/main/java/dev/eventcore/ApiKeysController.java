package dev.eventcore;

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

@RestController
@RequestMapping("/v1/api-keys")
class ApiKeysController {

    private final ApiKeyStore apiKeys;
    private final SecurityProperties security;

    ApiKeysController(ApiKeyStore apiKeys, SecurityProperties security) {
        this.apiKeys = apiKeys;
        this.security = security;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    IssuedApiKey issue(@RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
                       @RequestBody CreateApiKeyRequest request) {
        requireAdmin(adminToken);
        request.validate();
        return apiKeys.issue(request.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
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
