package com.linkflow.app;

import com.linkflow.analytics.application.service.AnalyticsFlushService;
import com.jayway.jsonpath.JsonPath;
import com.linkflow.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalyticsAndCacheIT extends AbstractIntegrationTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AnalyticsFlushService analyticsFlushService;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        String email = "analytics-" + System.nanoTime() + "@example.com";
        registerUser(email, "StrongP@ss1", "Analytics");
        accessToken = login(email, "StrongP@ss1").accessToken();
    }

    @Test
    void redirectTracksClicksAndPopulatesRedisCache() throws Exception {
        String createJson = mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "originalUrl": "https://example.com/analytics-track" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String urlId = JsonPath.read(createJson, "$.data.id");
        String shortCode = JsonPath.read(createJson, "$.data.shortCode");

        mockMvc.perform(get("/r/" + shortCode))
                .andExpect(status().isFound());

        String cacheKey = "url:shortcode:" + shortCode.toLowerCase();
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        assertNotNull(cached, "Redirect should populate Redis cache");
        assertTrue(cached.contains("https://example.com/analytics-track"));

        awaitClickCount(urlId, 1);
    }

    private void awaitClickCount(String urlId, int expected) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            analyticsFlushService.flush();
            String json = mockMvc.perform(get("/api/v1/urls/" + urlId + "/analytics")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            int clicks = JsonPath.read(json, "$.data.totalClicks");
            if (clicks >= expected) {
                assertEquals(expected, clicks);
                return;
            }
            Thread.sleep(100);
        }
        fail("Expected at least " + expected + " clicks within timeout");
    }

    @Test
    void topUrlsEndpointReturnsCreatedUrl() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "originalUrl": "https://example.com/top-url" }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/analytics/top?limit=5")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void recentClickEventsEndpointReturnsTrackedClick() throws Exception {
        String createJson = mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "originalUrl": "https://example.com/click-history" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String urlId = JsonPath.read(createJson, "$.data.id");
        String shortCode = JsonPath.read(createJson, "$.data.shortCode");

        mockMvc.perform(get("/r/" + shortCode))
                .andExpect(status().isFound());

        awaitClickEvents(urlId, 1);

        mockMvc.perform(get("/api/v1/urls/" + urlId + "/analytics/clicks?limit=5")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].clickedAt").exists());
    }

    private void awaitClickEvents(String urlId, int expected) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            analyticsFlushService.flush();
            String json = mockMvc.perform(get("/api/v1/urls/" + urlId + "/analytics/clicks?limit=5")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            int count = JsonPath.read(json, "$.data.length()");
            if (count >= expected) {
                assertEquals(expected, count);
                return;
            }
            Thread.sleep(100);
        }
        fail("Expected at least " + expected + " click events within timeout");
    }
}
