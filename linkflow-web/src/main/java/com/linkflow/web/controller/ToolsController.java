package com.linkflow.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkflow.web.client.ActuatorApiClient;
import com.linkflow.web.client.ApiCallHelper;
import com.linkflow.web.config.WebClientConfig;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/tools")
@RequiredArgsConstructor
public class ToolsController {

    private final ApiCallHelper apiCallHelper;
    private final ActuatorApiClient actuatorApiClient;
    private final WebClientConfig webClientConfig;

    @GetMapping("/rate-limit")
    public String rateLimit(Model model) {
        model.addAttribute("userRpm", webClientConfig.getRateLimit().getUserRpm());
        model.addAttribute("ipRpm", webClientConfig.getRateLimit().getIpRpm());
        model.addAttribute("pageTitle", "Rate Limit Demo");
        model.addAttribute("activeNav", "tools-rate-limit");
        return "tools/rate-limit";
    }

    @GetMapping("/rate-limit/probe")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rateLimitProbe(
            @RequestParam(defaultValue = "10") int n,
            @RequestParam(defaultValue = "/api/v1/urls") String endpoint,
            HttpSession session) {
        int count = Math.max(1, Math.min(n, 200));
        var authState = apiCallHelper.requireAuth(session);
        List<ActuatorApiClient.RateLimitProbeResult> results =
                actuatorApiClient.probe(authState.accessToken(), endpoint, count);

        boolean has429 = results.stream().anyMatch(r -> r.status() == 429);
        String message429 = results.stream()
                .filter(r -> r.status() == 429)
                .map(ActuatorApiClient.RateLimitProbeResult::message)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse(null);

        return ResponseEntity.ok(Map.of(
                "results", results.stream().map(ActuatorApiClient.RateLimitProbeResult::toMap).toList(),
                "has429", has429,
                "message429", message429 != null ? message429 : ""
        ));
    }


}
