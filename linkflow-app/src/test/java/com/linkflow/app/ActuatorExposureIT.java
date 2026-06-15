package com.linkflow.app;

import com.linkflow.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("prod")
class ActuatorExposureIT extends AbstractIntegrationTest {

    @Test
    void healthEndpointIsPublicInProdProfile() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
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
