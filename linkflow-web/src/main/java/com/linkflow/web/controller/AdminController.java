package com.linkflow.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkflow.web.client.ActuatorApiClient;
import com.linkflow.web.client.AnalyticsApiClient;
import com.linkflow.web.client.ApiCallHelper;
import com.linkflow.web.client.UrlApiClient;
import com.linkflow.web.client.UserApiClient;
import com.linkflow.web.config.WebClientConfig;
import com.linkflow.web.dto.analytics.ClickTrendResponse;
import com.linkflow.web.dto.analytics.SystemStatsResponse;
import com.linkflow.web.dto.analytics.TopUrlResponse;
import com.linkflow.web.dto.common.PagedResponse;
import com.linkflow.web.dto.url.UrlResponse;
import com.linkflow.web.dto.user.UserResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ApiCallHelper apiCallHelper;
    private final AnalyticsApiClient analyticsApiClient;
    private final UserApiClient userApiClient;
    private final UrlApiClient urlApiClient;
    private final ActuatorApiClient actuatorApiClient;
    private final WebClientConfig webClientConfig;
    private final ObjectMapper objectMapper;

    @GetMapping({"", "/"})
    public String dashboard(HttpSession session, Model model) throws JsonProcessingException {
        SystemStatsResponse stats = apiCallHelper.withTokenRefresh(session, auth ->
                analyticsApiClient.getSystemStats(auth.accessToken())
        );
        List<TopUrlResponse> topUrls = apiCallHelper.withTokenRefresh(session, auth ->
                analyticsApiClient.getAdminTopUrls(auth.accessToken(), 10)
        );
        List<com.linkflow.web.dto.analytics.ClickEventResponse> recentClicks = apiCallHelper.withTokenRefresh(session, auth ->
                analyticsApiClient.getSystemRecentClicks(auth.accessToken(), 10)
        );
        model.addAttribute("stats", stats);
        model.addAttribute("topUrls", topUrls);
        model.addAttribute("topUrlsJson", objectMapper.writeValueAsString(topUrls));
        model.addAttribute("recentClicks", recentClicks);
        model.addAttribute("pageTitle", "Admin Dashboard");
        model.addAttribute("activeNav", "admin-dashboard");
        model.addAttribute("adminSection", true);
        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String users(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        @RequestParam(defaultValue = "createdAt") String sortBy,
                        @RequestParam(defaultValue = "desc") String direction,
                        HttpSession session,
                        Model model) {
        PagedResponse<UserResponse> users = apiCallHelper.withTokenRefresh(session, auth ->
                userApiClient.listAdminUsers(auth.accessToken(), page, size, sortBy, direction)
        );
        model.addAttribute("users", users);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("direction", direction);
        model.addAttribute("pageTitle", "Users");
        model.addAttribute("activeNav", "admin-users");
        model.addAttribute("adminSection", true);
        return "admin/users";
    }

    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable UUID id, HttpSession session, Model model) {
        UserResponse user = apiCallHelper.withTokenRefresh(session, auth ->
                userApiClient.getAdminUser(auth.accessToken(), id)
        );
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "User Details");
        model.addAttribute("activeNav", "admin-users");
        model.addAttribute("adminSection", true);
        return "admin/user-detail";
    }

    @GetMapping("/urls")
    public String urls(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(defaultValue = "createdAt") String sortBy,
                       @RequestParam(defaultValue = "desc") String direction,
                       HttpSession session,
                       Model model) {
        PagedResponse<UrlResponse> urls = apiCallHelper.withTokenRefresh(session, auth ->
                urlApiClient.listAdminUrls(auth.accessToken(), page, size, sortBy, direction)
        );
        model.addAttribute("urls", urls);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("direction", direction);
        model.addAttribute("pageTitle", "All URLs");
        model.addAttribute("activeNav", "admin-urls");
        model.addAttribute("adminSection", true);
        return "admin/urls";
    }

    @PostMapping("/urls/{id}/deactivate")
    public String deactivateUrl(@PathVariable UUID id,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        apiCallHelper.withTokenRefresh(session, auth ->
                urlApiClient.adminDeactivate(auth.accessToken(), id)
        );
        redirectAttributes.addFlashAttribute("successMessage", "URL deactivated.");
        return "redirect:/admin/urls";
    }

    @PostMapping("/urls/{id}/reactivate")
    public String reactivateUrl(@PathVariable UUID id,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        apiCallHelper.withTokenRefresh(session, auth ->
                urlApiClient.adminReactivate(auth.accessToken(), id)
        );
        redirectAttributes.addFlashAttribute("successMessage", "URL reactivated.");
        return "redirect:/admin/urls";
    }

    @GetMapping("/analytics")
    public String analytics(HttpSession session, Model model) throws JsonProcessingException {
        SystemStatsResponse stats = apiCallHelper.withTokenRefresh(session, auth ->
                analyticsApiClient.getSystemStats(auth.accessToken())
        );
        List<TopUrlResponse> topUrls = apiCallHelper.withTokenRefresh(session, auth ->
                analyticsApiClient.getAdminTopUrls(auth.accessToken(), 10)
        );
        List<ClickTrendResponse> trend7d = apiCallHelper.withTokenRefresh(session, auth ->
                analyticsApiClient.getSystemClickTrend(auth.accessToken(), 7)
        );
        List<ClickTrendResponse> trend30d = apiCallHelper.withTokenRefresh(session, auth ->
                analyticsApiClient.getSystemClickTrend(auth.accessToken(), 30)
        );
        List<ClickTrendResponse> trend90d = apiCallHelper.withTokenRefresh(session, auth ->
                analyticsApiClient.getSystemClickTrend(auth.accessToken(), 90)
        );

        model.addAttribute("stats", stats);
        model.addAttribute("topUrls", topUrls);
        model.addAttribute("topUrlsJson", objectMapper.writeValueAsString(topUrls));
        model.addAttribute("statsJson", objectMapper.writeValueAsString(stats));
        model.addAttribute("trend7dJson", objectMapper.writeValueAsString(trend7d));
        model.addAttribute("trend30dJson", objectMapper.writeValueAsString(trend30d));
        model.addAttribute("trend90dJson", objectMapper.writeValueAsString(trend90d));
        model.addAttribute("pageTitle", "System Analytics");
        model.addAttribute("activeNav", "admin-analytics");
        model.addAttribute("adminSection", true);
        return "admin/analytics";
    }

    @GetMapping("/system")
    public String system(HttpSession session, Model model) {
        var health = actuatorApiClient.getHealth();
        model.addAttribute("health", health);
        model.addAttribute("grafanaUrl", webClientConfig.getGrafanaUrl());
        model.addAttribute("prometheusUrl", webClientConfig.getPrometheusUrl());
        model.addAttribute("gatewayUrl", webClientConfig.getPublicGatewayUrl());
        model.addAttribute("userRpm", webClientConfig.getRateLimit().getUserRpm());
        model.addAttribute("ipRpm", webClientConfig.getRateLimit().getIpRpm());
        model.addAttribute("pageTitle", "System Health");
        model.addAttribute("activeNav", "admin-system");
        model.addAttribute("adminSection", true);
        return "admin/system";
    }
}
