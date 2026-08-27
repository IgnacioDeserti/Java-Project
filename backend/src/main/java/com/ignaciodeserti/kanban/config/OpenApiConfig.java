package com.ignaciodeserti.kanban.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI kanbanOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Kanban Board API")
                                .description(
                                        "REST API for a Trello-like kanban board: boards, columns, cards, "
                                                + "JWT auth with refresh tokens, and Google sign-in. "
                                                + "Real-time board updates are pushed over a separate STOMP/WebSocket "
                                                + "endpoint at /ws — not shown here, since it isn't a REST resource.")
                                .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER_SCHEME,
                                        new SecurityScheme()
                                                .name(BEARER_SCHEME)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description(
                                                        "Paste the access token from /api/auth/login or /api/auth/register — no \"Bearer \" prefix needed here, Swagger adds it.")));
    }
}
