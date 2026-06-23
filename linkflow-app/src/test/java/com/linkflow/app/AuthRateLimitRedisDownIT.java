package com.linkflow.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone integration test that verifies fail-closed rate limiting
 * when Redis is unavailable on auth paths.
 *
 * Does NOT extend AbstractIntegrationTest because the parent class provides
 * a working Redis container via @DynamicPropertySource which cannot be
 * reliably overridden by subclass methods (parent methods run last).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthRateLimitRedisDownIT {

    private static final String JWT_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1pbnRlZ3JhdGlvbi10ZXN0cy1taW5pbXVtLTY0LWNoYXJz";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("linkflow")
            .withUsername("linkflow")
            .withPassword("linkflow");

    static {
        if (org.testcontainers.DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Point Redis to a non-routable IP (RFC 5737 TEST-NET-1)
        registry.add("spring.data.redis.host", () -> "192.0.2.1");
        registry.add("spring.data.redis.port", () -> 6399);
        registry.add("spring.data.redis.timeout", () -> "200ms");
        registry.add("spring.data.redis.connect-timeout", () -> "200ms");
        registry.add("linkflow.jwt.secret", () -> JWT_SECRET);
        registry.add("linkflow.rate-limit.user-rpm", () -> 100);
        registry.add("linkflow.rate-limit.ip-rpm", () -> 200);
        registry.add("linkflow.rate-limit.auth-fail-closed", () -> true);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void authLoginReturnsServiceUnavailableWhenRedisIsDown() throws Exception {
        String body = """
                {
                  "email": "missing@example.com",
                  "password": "StrongP@ss1"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_BACKEND_UNAVAILABLE"));
    }
}
