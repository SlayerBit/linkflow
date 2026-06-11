package com.linkflow.app;

import com.jayway.jsonpath.JsonPath;
import com.linkflow.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UrlFlowIT extends AbstractIntegrationTest {

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        String email = "url-" + System.nanoTime() + "@example.com";
        registerUser(email, "StrongP@ss1", "Url");
        accessToken = login(email, "StrongP@ss1").accessToken();
    }

    @Test
    void createUrlWithCustomAliasAndRedirect() throws Exception {
        String createJson = mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/custom",
                                  "customAlias": "my-link-%d"
                                }
                                """.formatted(System.nanoTime() % 100000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shortCode").exists())
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn().getResponse().getContentAsString();

        String shortCode = JsonPath.read(createJson, "$.data.shortCode");

        mockMvc.perform(get("/r/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/custom"));
    }

    @Test
    void bulkCreateRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/urls/bulk")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "urls": [
                                    { "originalUrl": "https://example.com/1" }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkCreateAndIdempotency() throws Exception {
        String idempotencyKey = "bulk-key-" + System.nanoTime();
        String bulkBody = """
                {
                  "urls": [
                    { "originalUrl": "https://example.com/bulk-1" },
                    { "originalUrl": "https://example.com/bulk-2", "customAlias": "bulk-%d" }
                  ]
                }
                """.formatted(System.nanoTime() % 100000);

        String first = mockMvc.perform(post("/api/v1/urls/bulk")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.count").value(2))
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/v1/urls/bulk")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertEquals(
                JsonPath.read(first, "$.data.urls[0].id").toString(),
                JsonPath.read(second, "$.data.urls[0].id").toString());
    }

    @Test
    void singleCreateIdempotencyReturnsSameResponse() throws Exception {
        String idempotencyKey = "single-key-" + System.nanoTime();
        String body = """
                { "originalUrl": "https://example.com/idempotent" }
                """;

        String first = mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertEquals(
                JsonPath.read(first, "$.data.id").toString(),
                JsonPath.read(second, "$.data.id").toString());
    }

    @Test
    void expiredUrlReturnsGone() throws Exception {
        Instant expiresAt = Instant.now().plus(2, ChronoUnit.SECONDS);
        String createJson = mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/expired",
                                  "expiresAt": "%s"
                                }
                                """.formatted(expiresAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String shortCode = JsonPath.read(createJson, "$.data.shortCode");
        Thread.sleep(2500);

        mockMvc.perform(get("/r/" + shortCode))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.errorCode").value("GONE"));
    }

    @Test
    void deactivatedUrlReturnsGone() throws Exception {
        String createJson = mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "originalUrl": "https://example.com/deactivated" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String urlId = JsonPath.read(createJson, "$.data.id");
        String shortCode = JsonPath.read(createJson, "$.data.shortCode");

        mockMvc.perform(patch("/api/v1/urls/" + urlId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "active": false }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/r/" + shortCode))
                .andExpect(status().isGone());
    }

    @Test
    void unknownShortCodeReturnsNotFound() throws Exception {
        mockMvc.perform(get("/r/does-not-exist-xyz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }
}
