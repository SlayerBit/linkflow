package com.linkflow.app;

import com.linkflow.app.support.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the hot paths that publish business metrics and asserts both the in-process registry
 * and the Prometheus scrape text expose them. The scrape names are the contract Grafana and the
 * alert rules depend on — renaming a counter without updating those blanks the dashboards silently.
 * <p>
 * {@link AutoConfigureObservability} is required: Spring Boot disables metrics export in tests
 * unless asked, which would leave {@code /actuator/prometheus} unmapped and this test asserting
 * nothing about the scrape contract.
 */
@AutoConfigureObservability
class BusinessMetricsIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "StrongP@ss1";

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void hotPathTrafficProducesBusinessCountersOnThePrometheusScrape() throws Exception {
        String email = "metrics-" + System.nanoTime() + "@example.com";
        registerUser(email, PASSWORD, "Metrics");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "WrongP@ss9"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());

        String accessToken = login(email, PASSWORD).accessToken();
        String createBody = mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl": "https://example.com/metrics-target"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String shortCode = com.jayway.jsonpath.JsonPath.read(createBody, "$.data.shortCode");

        mockMvc.perform(get("/r/" + shortCode))
                .andExpect(status().isFound());

        assertTrue(meterRegistry.find("linkflow.auth.login").counters().stream()
                        .anyMatch(c -> c.count() > 0),
                "login counter should have been incremented");
        assertTrue(meterRegistry.find("linkflow.auth.register").counter() != null
                        && meterRegistry.find("linkflow.auth.register").counter().count() > 0,
                "register counter should have been incremented");
        assertTrue(meterRegistry.find("linkflow.url.creations").counters().stream()
                        .anyMatch(c -> c.count() > 0),
                "url creation counter should have been incremented");
        assertTrue(meterRegistry.find("linkflow.redirects").counters().stream()
                        .anyMatch(c -> c.count() > 0),
                "redirect counter should have been incremented");
        assertTrue(meterRegistry.find("linkflow.url_cache").counters().stream()
                        .anyMatch(c -> c.count() > 0),
                "cache lookup counter should have been incremented");

        String scrape = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(scrape.contains("linkflow_auth_login_total"), scrape);
        assertTrue(scrape.contains("linkflow_auth_register_total"), scrape);
        assertTrue(scrape.contains("linkflow_url_creations_total"), scrape);
        assertTrue(scrape.contains("linkflow_redirects_total"), scrape);
        assertTrue(scrape.contains("linkflow_url_cache_total"), scrape);
        assertTrue(scrape.contains("linkflow_info"), scrape);
    }
}
