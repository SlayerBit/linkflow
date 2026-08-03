package com.linkflow.app;

import com.linkflow.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the security properties the API is supposed to guarantee, rather than trusting that the
 * configuration expressing them is still wired up.
 */
class SecurityHardeningIT extends AbstractIntegrationTest {

    private static final int IP_RPM = 3;

    @DynamicPropertySource
    static void lowIpRateLimit(DynamicPropertyRegistry registry) {
        registry.add("linkflow.rate-limit.ip-rpm", () -> IP_RPM);
        registry.add("linkflow.rate-limit.user-rpm", () -> IP_RPM);
        // No proxy is trusted, matching the default posture.
        registry.add("linkflow.trusted-proxies.cidrs", () -> "");
    }

    @Test
    void spoofedForwardedForCannotBuyExtraRateLimitBudget() throws Exception {
        // Every request presents a different X-Forwarded-For. If the header were honoured from an
        // untrusted peer, each would land in its own bucket and nothing would ever be throttled.
        int attempts = IP_RPM * 3;
        int allowed = 0;
        int throttled = 0;

        for (int i = 0; i < attempts; i++) {
            int status = mockMvc.perform(get("/api/v1/urls")
                            .header("X-Forwarded-For", "203.0.113." + i))
                    .andReturn().getResponse().getStatus();

            if (status == 429) {
                throttled++;
            } else {
                allowed++;
            }
        }

        // Asserted as a bound rather than an exact count so the result does not depend on what
        // other tests in this class already spent from the shared bucket.
        assertTrue(allowed <= IP_RPM,
                "Spoofed X-Forwarded-For bought extra budget: " + allowed
                        + " requests were allowed with a limit of " + IP_RPM);
        assertTrue(throttled > 0, "Expected IP rate limiting to reject some requests");
    }

    @Test
    void throttledResponseIdentifiesTheRateLimit() throws Exception {
        for (int i = 0; i < IP_RPM; i++) {
            mockMvc.perform(get("/api/v1/urls"));
        }

        mockMvc.perform(get("/api/v1/urls"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void apiResponsesCarryHardeningHeaders() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'"));
    }
}
