package com.linkflow.analytics.api.controller;

import com.linkflow.analytics.api.dto.ClickEventResponse;
import com.linkflow.analytics.api.dto.TopUrlResponse;
import com.linkflow.analytics.api.dto.UrlAnalyticsResponse;
import com.linkflow.analytics.application.service.AnalyticsQueryService;
import com.linkflow.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "URL click analytics")
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    @GetMapping("/api/v1/urls/{id}/analytics")
    @Operation(summary = "Get analytics for a specific URL (owner only)")
    public ResponseEntity<ApiResponse<UrlAnalyticsResponse>> getUrlAnalytics(@PathVariable("id") UUID id) {
        UrlAnalyticsResponse analytics = analyticsQueryService.getUrlAnalytics(id);
        return ResponseEntity.ok(ApiResponse.of(analytics));
    }

    @GetMapping("/api/v1/analytics/top")
    @Operation(summary = "Get current user's top URLs by clicks")
    public ResponseEntity<ApiResponse<List<TopUrlResponse>>> getTopUrls(
            @RequestParam(defaultValue = "10") int limit) {
        limit = Math.min(Math.max(limit, 1), 100);
        List<TopUrlResponse> topUrls = analyticsQueryService.getTopUrlsForCurrentUser(limit);
        return ResponseEntity.ok(ApiResponse.of(topUrls));
    }

    @GetMapping("/api/v1/urls/{id}/analytics/clicks")
    @Operation(summary = "Get recent click events for a URL (owner only)")
    public ResponseEntity<ApiResponse<List<ClickEventResponse>>> getRecentClicks(
            @PathVariable("id") UUID id,
            @RequestParam(defaultValue = "20") int limit) {
        List<ClickEventResponse> clicks = analyticsQueryService.getRecentClicksForUrl(id, limit);
        return ResponseEntity.ok(ApiResponse.of(clicks));
    }
}
