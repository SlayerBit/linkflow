package com.linkflow.app;

import com.linkflow.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Observability is opted into so {@code /actuator/prometheus} is actually mapped. Without
 * {@link AutoConfigureObservability}, Spring Boot leaves metrics export off in tests and a deny
 * rule on an unmapped path is indistinguishable from a real authorization check.
 */
@ActiveProfiles("prod")
@AutoConfigureObservability
class ActuatorExposureIT extends AbstractIntegrationTest {

    @Test
    void healthEndpointIsPublicInProdProfile() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    /**
     * The probe groups sit beneath {@code /actuator/health}, so a rule that permits only the exact
     * path leaves them authenticated. Container and orchestrator probes are unauthenticated by
     * nature, and the failure is quiet in the worst way: the request is answered with a redirect or
     * a login page, which anything checking only the status code reads as healthy.
     */
    @Test
    void livenessAndReadinessProbesArePublicInProdProfile() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                // Readiness covers the database and Redis, both of which are real containers here,
                // so UP also confirms those contributors are wired into the group.
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(content().contentTypeCompatibleWith("application/vnd.spring-boot.actuator.v3+json"));
    }

    /**
     * Distinct from the Prometheus endpoint: this one is a browsable index of every metric name with
     * per-metric queries, and it stays denied even where scraping is permitted.
     */
    @Test
    void metricsIndexIsDeniedWhenMetricsNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void prometheusEndpointIsDeniedWhenMetricsNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerUiIsDeniedInProdProfile() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized());
    }
}
