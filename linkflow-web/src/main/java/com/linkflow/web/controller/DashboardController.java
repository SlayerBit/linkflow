package com.linkflow.web.controller;

import com.linkflow.web.client.AnalyticsApiClient;
import com.linkflow.web.client.ApiCallHelper;
import com.linkflow.web.client.UrlApiClient;
import com.linkflow.web.dto.analytics.TopUrlResponse;
import com.linkflow.web.dto.common.PagedResponse;
import com.linkflow.web.dto.url.UrlResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final ApiCallHelper apiCallHelper;
    private final UrlApiClient urlApiClient;
    private final AnalyticsApiClient analyticsApiClient;
    private final ObjectMapper objectMapper;

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) throws JsonProcessingException {
        PagedResponse<UrlResponse> recentUrls = apiCallHelper.withTokenRefresh(session, auth ->
                urlApiClient.listUserUrls(auth.accessToken(), 0, 5, "createdAt", "desc")
        );
        List<TopUrlResponse> topUrls = apiCallHelper.withTokenRefresh(session, auth ->
                analyticsApiClient.getTopUrls(auth.accessToken(), 5)
        );
        List<com.linkflow.web.dto.analytics.ClickEventResponse> recentClicks = apiCallHelper.withTokenRefresh(session, auth ->
                analyticsApiClient.getRecentClicks(auth.accessToken(), 10)
        );

        model.addAttribute("recentUrls", recentUrls.content());
        model.addAttribute("topUrls", topUrls);
        model.addAttribute("topUrlsJson", objectMapper.writeValueAsString(topUrls));
        model.addAttribute("recentClicks", recentClicks);
        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("activeNav", "dashboard");
        return "user/dashboard";
    }
}
