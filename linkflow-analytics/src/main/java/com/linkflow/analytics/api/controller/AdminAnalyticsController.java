package com.linkflow.analytics.api.controller;

import com.linkflow.analytics.api.dto.ClickEventResponse;
import com.linkflow.analytics.api.dto.SystemStatsResponse;
import com.linkflow.analytics.api.dto.TopUrlResponse;
import com.linkflow.analytics.api.dto.UrlAnalyticsResponse;
import com.linkflow.analytics.application.service.AnalyticsQueryService;
import com.linkflow.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Analytics", description = "System-wide analytics")
public class AdminAnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    @GetMapping("/top")
    @Operation(summary = "Get system-wide top URLs by clicks")
    public ResponseEntity<ApiResponse<List<TopUrlResponse>>> getSystemTopUrls(
            @RequestParam(defaultValue = "10") int limit) {
        limit = Math.min(Math.max(limit, 1), 100);
        List<TopUrlResponse> topUrls = analyticsQueryService.getSystemTopUrls(limit);
        return ResponseEntity.ok(ApiResponse.of(topUrls));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get system-wide statistics")
    public ResponseEntity<ApiResponse<SystemStatsResponse>> getSystemStats() {
        SystemStatsResponse stats = analyticsQueryService.getSystemStats();
        return ResponseEntity.ok(ApiResponse.of(stats));
    }

    @GetMapping("/urls/{id}/clicks")
    @Operation(summary = "Get recent click events for any URL (admin)")
    public ResponseEntity<ApiResponse<List<ClickEventResponse>>> getRecentClicks(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "20") int limit) {
        List<ClickEventResponse> clicks = analyticsQueryService.getRecentClicksForUrlAsAdmin(id, limit);
        return ResponseEntity.ok(ApiResponse.of(clicks));
    }

    @GetMapping("/urls/{id}/click-trend")
    @Operation(summary = "Get click trend for any URL (admin)")
    public ResponseEntity<ApiResponse<List<com.linkflow.analytics.api.dto.ClickTrendResponse>>> getClickTrend(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "30") int days) {
        List<com.linkflow.analytics.api.dto.ClickTrendResponse> trend = analyticsQueryService.getClickTrendForUrlAsAdmin(id, days);
        return ResponseEntity.ok(ApiResponse.of(trend));
    }

    @GetMapping("/click-trend")
    @Operation(summary = "Get system-wide click trend (admin)")
    public ResponseEntity<ApiResponse<List<com.linkflow.analytics.api.dto.ClickTrendResponse>>> getSystemClickTrend(
            @RequestParam(defaultValue = "30") int days) {
        List<com.linkflow.analytics.api.dto.ClickTrendResponse> trend = analyticsQueryService.getSystemClickTrend(days);
        return ResponseEntity.ok(ApiResponse.of(trend));
    }

    @GetMapping("/recent-clicks")
    @Operation(summary = "Get platform-wide recent click events (admin)")
    public ResponseEntity<ApiResponse<List<ClickEventResponse>>> getSystemRecentClicks(
            @RequestParam(defaultValue = "10") int limit) {
        limit = Math.min(Math.max(limit, 1), 100);
        List<ClickEventResponse> clicks = analyticsQueryService.getSystemRecentClicks(limit);
        return ResponseEntity.ok(ApiResponse.of(clicks));
    }

    @GetMapping("/urls/{id}")
    @Operation(summary = "Get analytics stats for a specific URL (admin)")
    public ResponseEntity<ApiResponse<UrlAnalyticsResponse>> getUrlAnalytics(@PathVariable("id") UUID id) {
        UrlAnalyticsResponse analytics = analyticsQueryService.getUrlAnalyticsAsAdmin(id);
        return ResponseEntity.ok(ApiResponse.of(analytics));
    }
}
