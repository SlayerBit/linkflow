package com.linkflow.app;

import com.linkflow.app.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserManagementIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void adminBootstrap(DynamicPropertyRegistry registry) {
        registry.add("linkflow.bootstrap.admin.enabled", () -> true);
        registry.add("linkflow.bootstrap.admin.email", () -> "admin-users-it@linkflow.test");
        registry.add("linkflow.bootstrap.admin.password", () -> "AdminP@ss1");
    }

    @Test
    void adminCanDisableAndReenableUser() throws Exception {
        String adminToken = login("admin-users-it@linkflow.test", "AdminP@ss1").accessToken();

        String email = "managed-" + System.nanoTime() + "@example.com";
        String userId = registerAndExtractUserId(email);

        mockMvc.perform(patch("/api/v1/admin/users/" + userId + "/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"StrongP@ss1"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/admin/users/" + userId + "/enable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"StrongP@ss1"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanSoftDeleteUser() throws Exception {
        String adminToken = login("admin-users-it@linkflow.test", "AdminP@ss1").accessToken();
        String email = "deleted-" + System.nanoTime() + "@example.com";
        String userId = registerAndExtractUserId(email);

        mockMvc.perform(delete("/api/v1/admin/users/" + userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    private String registerAndExtractUserId(String email) throws Exception {
        String json = registerUser(email, "StrongP@ss1", "User");
        return JsonPath.read(json, "$.data.id");
    }
}
