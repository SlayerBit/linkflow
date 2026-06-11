package com.linkflow.app;

import com.linkflow.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitIT extends AbstractIntegrationTest {

    private String accessToken;

    @DynamicPropertySource
    static void lowRateLimit(DynamicPropertyRegistry registry) {
        registry.add("linkflow.rate-limit.user-rpm", () -> 3);
        registry.add("linkflow.rate-limit.ip-rpm", () -> 3);
    }

    @BeforeEach
    void setUp() throws Exception {
        String email = "ratelimit-" + System.nanoTime() + "@example.com";
        registerUser(email, "StrongP@ss1", "Rate");
        accessToken = login(email, "StrongP@ss1").accessToken();
    }

    @Test
    void authenticatedRequestsReturnRateLimitHeadersAndEventually429() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/users/me")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .header().exists("X-RateLimit-Limit"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .header().exists("X-RateLimit-Remaining"));
        }

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }
}
