package com.example.taskmanagement.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI at /swagger-ui.html. Log in with POST /api/auth/login, then paste the token
 * into "Authorize" to call the secured endpoints.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Task Management API",
                version = "1.0",
                description = "Multi-tenant task API: JWT + roles, per-tenant queries (404 across tenants), "
                        + "optimistic locking (409), token-bucket rate limiting (429). "
                        + "Demo admin: admin@example.com / admin123, or register your own user."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
