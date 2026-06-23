package com.linkflow.app;

import com.jayway.jsonpath.JsonPath;
import com.linkflow.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSelfProtectionIT extends AbstractIntegrationTest {

        @DynamicPropertySource
        static void adminBootstrap(DynamicPropertyRegistry registry) {
                registry.add("linkflow.bootstrap.admin.enabled", () -> true);
                registry.add("linkflow.bootstrap.admin.email", () -> "admin-self-it@linkflow.test");
                registry.add("linkflow.bootstrap.admin.password", () -> "AdminP@ss1");
        }

        @Test
        void adminCannotDisableSelf() throws Exception {
                String adminToken = login("admin-self-it@linkflow.test", "AdminP@ss1").accessToken();
                String adminId = extractUserId(adminToken);

                mockMvc.perform(patch("/api/v1/admin/users/" + adminId + "/disable")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errorCode").value("ADMIN_SELF_ACTION_FORBIDDEN"));
        }

        @Test
        void adminCannotDeleteSelf() throws Exception {
                String adminToken = login("admin-self-it@linkflow.test", "AdminP@ss1").accessToken();
                String adminId = extractUserId(adminToken);

                mockMvc.perform(delete("/api/v1/admin/users/" + adminId)
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errorCode").value("ADMIN_SELF_ACTION_FORBIDDEN"));
        }

        @Test
        void adminCannotDemoteSelf() throws Exception {
                String adminToken = login("admin-self-it@linkflow.test", "AdminP@ss1").accessToken();
                String adminId = extractUserId(adminToken);

                mockMvc.perform(patch("/api/v1/admin/users/" + adminId + "/roles")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "roles": ["USER"]
                                                }
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errorCode").value("ADMIN_SELF_ACTION_FORBIDDEN"));
        }

        @Test
        void demotingSecondAdminSucceedsWhenTwoAdminsExist() throws Exception {
                String adminToken = login("admin-self-it@linkflow.test", "AdminP@ss1").accessToken();

                String otherEmail = "other-admin-" + System.nanoTime() + "@example.com";
                String otherJson = registerUser(otherEmail, "StrongP@ss1", "Other");
                String otherId = JsonPath.read(otherJson, "$.data.id");

                mockMvc.perform(patch("/api/v1/admin/users/" + otherId + "/roles")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "roles": ["USER", "ADMIN"]
                                                }
                                                """))
                                .andExpect(status().isOk());

                mockMvc.perform(patch("/api/v1/admin/users/" + otherId + "/roles")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "roles": ["USER"]
                                                }
                                                """))
                                .andExpect(status().isOk());

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/admin/users/" + otherId)
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.roles[?(@ == 'USER')]").exists())
                                .andExpect(jsonPath("$.data.roles[?(@ == 'ADMIN')]").doesNotExist());
        }

        private String extractUserId(String accessToken) throws Exception {
                String json = mockMvc
                                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                                .get("/api/v1/users/me")
                                                .header("Authorization", "Bearer " + accessToken))
                                .andReturn().getResponse().getContentAsString();
                return JsonPath.read(json, "$.data.id");
        }
}
