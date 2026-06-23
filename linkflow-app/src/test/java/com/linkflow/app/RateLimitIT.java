package com.linkflow.app;

import com.linkflow.app.support.AbstractIntegrationTest;
import com.linkflow.ratelimit.infrastructure.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitIT extends AbstractIntegrationTest {

    private static final int TEST_USER_RPM = 3;

    @Autowired
    private RateLimitProperties rateLimitProperties;

    private String accessToken;

    @DynamicPropertySource
    static void lowRateLimit(DynamicPropertyRegistry registry) {
        registry.add("linkflow.rate-limit.user-rpm", () -> TEST_USER_RPM);
        registry.add("linkflow.rate-limit.ip-rpm", () -> TEST_USER_RPM);
    }

    @BeforeEach
    void setUp() throws Exception {
        assertEquals(TEST_USER_RPM, rateLimitProperties.getUserRpm());
        String email = "ratelimit-" + System.nanoTime() + "@example.com";
        registerUser(email, "StrongP@ss1", "Rate");
        accessToken = login(email, "StrongP@ss1").accessToken();
    }

    @Test
    void authenticatedRequestsReturnRateLimitHeadersAndEventually429() throws Exception {
        for (int i = 0; i < TEST_USER_RPM; i++) {
            mockMvc.perform(get("/api/v1/users/me")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", String.valueOf(TEST_USER_RPM)))
                    .andExpect(header().exists("X-RateLimit-Remaining"));
        }

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }
}
