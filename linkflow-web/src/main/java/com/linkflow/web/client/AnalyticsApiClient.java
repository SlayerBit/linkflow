package com.linkflow.web.client;

import com.linkflow.web.dto.analytics.ClickEventResponse;
import com.linkflow.web.dto.analytics.ClickTrendResponse;
import com.linkflow.web.dto.analytics.SystemStatsResponse;
import com.linkflow.web.dto.analytics.TopUrlResponse;
import com.linkflow.web.dto.analytics.UrlAnalyticsResponse;
import com.linkflow.web.dto.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AnalyticsApiClient {

    private final BackendClient backendClient;

    public UrlAnalyticsResponse getUrlAnalytics(String accessToken, UUID urlId) {
        var response = backendClient.exchangeForBody(
                backendClient.get("/api/v1/urls/" + urlId + "/analytics", accessToken),
                new ParameterizedTypeReference<ApiResponse<UrlAnalyticsResponse>>() {}
        );
        return response.data();
    }

    public List<TopUrlResponse> getTopUrls(String accessToken, int limit) {
        String uri = backendClient.buildUri("/api/v1/analytics/top", "limit", limit);
        var response = backendClient.exchangeForBody(
                backendClient.get(uri, accessToken),
                new ParameterizedTypeReference<ApiResponse<List<TopUrlResponse>>>() {}
        );
        return response.data();
    }

    public List<TopUrlResponse> getAdminTopUrls(String accessToken, int limit) {
        String uri = backendClient.buildUri("/api/v1/admin/analytics/top", "limit", limit);
        var response = backendClient.exchangeForBody(
                backendClient.get(uri, accessToken),
                new ParameterizedTypeReference<ApiResponse<List<TopUrlResponse>>>() {}
        );
        return response.data();
    }

    public SystemStatsResponse getSystemStats(String accessToken) {
        var response = backendClient.exchangeForBody(
                backendClient.get("/api/v1/admin/analytics/stats", accessToken),
                new ParameterizedTypeReference<ApiResponse<SystemStatsResponse>>() {}
        );
        return response.data();
    }

    public List<ClickTrendResponse> getClickTrend(String accessToken, UUID urlId, int days) {
        String uri = backendClient.buildUri("/api/v1/urls/" + urlId + "/analytics/click-trend", "days", days);
        var response = backendClient.exchangeForBody(
                backendClient.get(uri, accessToken),
                new ParameterizedTypeReference<ApiResponse<List<ClickTrendResponse>>>() {}
        );
        return response.data();
    }

    public List<ClickEventResponse> getRecentClicks(String accessToken, int limit) {
        String uri = backendClient.buildUri("/api/v1/analytics/recent-clicks", "limit", limit);
        var response = backendClient.exchangeForBody(
                backendClient.get(uri, accessToken),
                new ParameterizedTypeReference<ApiResponse<List<ClickEventResponse>>>() {}
        );
        return response.data();
    }

    public List<ClickTrendResponse> getAdminClickTrend(String accessToken, UUID urlId, int days) {
        String uri = backendClient.buildUri("/api/v1/admin/analytics/urls/" + urlId + "/click-trend", "days", days);
        var response = backendClient.exchangeForBody(
                backendClient.get(uri, accessToken),
                new ParameterizedTypeReference<ApiResponse<List<ClickTrendResponse>>>() {}
        );
        return response.data();
    }

    public List<ClickTrendResponse> getSystemClickTrend(String accessToken, int days) {
        String uri = backendClient.buildUri("/api/v1/admin/analytics/click-trend", "days", days);
        var response = backendClient.exchangeForBody(
                backendClient.get(uri, accessToken),
                new ParameterizedTypeReference<ApiResponse<List<ClickTrendResponse>>>() {}
        );
        return response.data();
    }

    public List<ClickEventResponse> getSystemRecentClicks(String accessToken, int limit) {
        String uri = backendClient.buildUri("/api/v1/admin/analytics/recent-clicks", "limit", limit);
        var response = backendClient.exchangeForBody(
                backendClient.get(uri, accessToken),
                new ParameterizedTypeReference<ApiResponse<List<ClickEventResponse>>>() {}
        );
        return response.data();
    }
}
