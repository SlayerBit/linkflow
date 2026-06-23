package com.linkflow.app;

import com.linkflow.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DisabledUserTokenRevocationIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void adminBootstrap(DynamicPropertyRegistry registry) {
        registry.add("linkflow.bootstrap.admin.enabled", () -> true);
        registry.add("linkflow.bootstrap.admin.email", () -> "admin-revoke-it@linkflow.test");
        registry.add("linkflow.bootstrap.admin.password", () -> "AdminP@ss1");
    }

    @Test
    void disabledUserAccessTokenIsRejectedImmediately() throws Exception {
        String adminToken = login("admin-revoke-it@linkflow.test", "AdminP@ss1").accessToken();
        String email = "revoke-" + System.nanoTime() + "@example.com";
        String registerJson = registerUser(email, "StrongP@ss1", "User");
        String userId = com.jayway.jsonpath.JsonPath.read(registerJson, "$.data.id");
        TokenPair tokens = login(email, "StrongP@ss1");

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/users/" + userId + "/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRevokesExistingAccessToken() throws Exception {
        String email = "pwd-" + System.nanoTime() + "@example.com";
        registerUser(email, "StrongP@ss1", "User");
        TokenPair tokens = login(email, "StrongP@ss1");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "StrongP@ss1",
                                  "newPassword": "NewStrongP@ss2"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"NewStrongP@ss2"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }
}
