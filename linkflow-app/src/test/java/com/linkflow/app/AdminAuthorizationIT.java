package com.linkflow.app;

import com.linkflow.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAuthorizationIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void adminBootstrap(DynamicPropertyRegistry registry) {
        registry.add("linkflow.bootstrap.admin.enabled", () -> true);
        registry.add("linkflow.bootstrap.admin.email", () -> "admin-it@linkflow.test");
        registry.add("linkflow.bootstrap.admin.password", () -> "AdminP@ss1");
    }

    @Test
    void regularUserCannotAccessAdminEndpoints() throws Exception {
        String email = "regular-" + System.nanoTime() + "@example.com";
        registerUser(email, "StrongP@ss1", "Regular");
        String token = login(email, "StrongP@ss1").accessToken();

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void adminUserCanAccessAdminEndpoints() throws Exception {
        String token = login("admin-it@linkflow.test", "AdminP@ss1").accessToken();

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void unauthenticatedAdminRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanAccessAdminUrlListing() throws Exception {
        String token = login("admin-it@linkflow.test", "AdminP@ss1").accessToken();

        mockMvc.perform(get("/api/v1/admin/urls")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void regularUserCannotAccessAdminAnalytics() throws Exception {
        String email = "no-analytics-admin-" + System.nanoTime() + "@example.com";
        registerUser(email, "StrongP@ss1", "User");
        String token = login(email, "StrongP@ss1").accessToken();

        mockMvc.perform(get("/api/v1/admin/analytics/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessSystemStats() throws Exception {
        String token = login("admin-it@linkflow.test", "AdminP@ss1").accessToken();

        mockMvc.perform(get("/api/v1/admin/analytics/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").isNumber());
    }
}
