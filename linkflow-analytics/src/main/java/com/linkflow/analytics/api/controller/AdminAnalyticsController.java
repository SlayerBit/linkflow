package com.linkflow.analytics.api.controller;

import com.linkflow.analytics.api.dto.ClickEventResponse;
import com.linkflow.analytics.api.dto.SystemStatsResponse;
import com.linkflow.analytics.api.dto.TopUrlResponse;
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
}
