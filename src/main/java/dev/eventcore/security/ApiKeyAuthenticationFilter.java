package dev.eventcore.security;

import dev.eventcore.api.ApiError;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyStore apiKeys;
    private final ObjectMapper json;

    ApiKeyAuthenticationFilter(ApiKeyStore apiKeys, ObjectMapper json) {
        this.apiKeys = apiKeys;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isProtected(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (holdsARecognizedKey(request)) {
            chain.doFilter(request, response);
        } else {
            reject(response);
        }
    }

    private static boolean isProtected(String path) {
        return path.startsWith("/v1/") && !path.startsWith("/v1/api-keys");
    }

    private boolean holdsARecognizedKey(HttpServletRequest request) {
        String presented = request.getHeader(API_KEY_HEADER);
        return presented != null && apiKeys.recognizes(presented);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(json.writeValueAsString(
                new ApiError("a valid " + API_KEY_HEADER + " header is required")));
    }
}