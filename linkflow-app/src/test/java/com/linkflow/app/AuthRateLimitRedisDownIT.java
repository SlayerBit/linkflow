package com.linkflow.app;

import com.linkflow.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "linkflow.rate-limit.auth-fail-closed=true"
})
class AuthRateLimitRedisDownIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void breakRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.port", () -> 6399);
    }

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
