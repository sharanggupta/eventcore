package dev.eventcore.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;

@Configuration
class OpenApiConfiguration {

    static {
        // Payloads are arbitrary JSON; stop springdoc expanding Jackson's JsonNode internals.
        SpringDocUtils.getConfig().replaceWithSchema(JsonNode.class,
                new ObjectSchema().description("Arbitrary JSON"));
    }

    @Bean
    OpenAPI eventCoreApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("EventCore API")
                        .version("v1")
                        .description("""
                                Self-hosted event audit log with reliable, signed webhook delivery.

                                Send events over HTTP; EventCore stores them durably in TimescaleDB, \
                                lets you query them with cursor pagination and type filtering, and \
                                pushes each one to your registered webhooks - HMAC-signed and retried \
                                with exponential backoff until delivered.

                                All /v1 endpoints except API-key management require an `X-API-Key` \
                                header. Key management requires the `X-Admin-Token` configured at \
                                deployment. Errors always have the shape `{"error": "<what went wrong>"}`.""")
                        .license(new License().name("Apache 2.0")))
                .components(new Components()
                        .addSecuritySchemes("apiKey", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("Issued via POST /v1/api-keys"))
                        .addSecuritySchemes("adminToken", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Admin-Token")
                                .description("The ADMIN_TOKEN configured at deployment")))
                .addSecurityItem(new SecurityRequirement().addList("apiKey"));
    }
}
