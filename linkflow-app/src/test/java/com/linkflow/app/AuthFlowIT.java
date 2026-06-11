package com.linkflow.app;

import com.linkflow.app.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIT extends AbstractIntegrationTest {

    @Test
    void registerLoginRefreshLogoutFlow() throws Exception {
        String email = "auth-flow-" + System.nanoTime() + "@example.com";
        String password = "StrongP@ss1";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "firstName": "Auth",
                                  "lastName": "Flow"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));

        TokenPair tokens = login(email, password);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(tokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());

        String refreshJson = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(tokens.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String errorCode = JsonPath.read(refreshJson, "$.errorCode");
        org.junit.jupiter.api.Assertions.assertNotNull(errorCode);

        TokenPair freshTokens = login(email, password);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + freshTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(freshTokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Logged out successfully"));
    }

    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        String email = "dup-" + System.nanoTime() + "@example.com";
        String password = "StrongP@ss1";
        registerUser(email, password, "Dup");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "firstName": "Dup",
                                  "lastName": "Again"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    void invalidCredentialsReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "missing@example.com",
                                  "password": "WrongPass1!"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
