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
import java.util.Set;

@Controller
@RequestMapping("/tools")
@RequiredArgsConstructor
public class ToolsController {

    private final ApiCallHelper apiCallHelper;
    private final ActuatorApiClient actuatorApiClient;
    private final WebClientConfig webClientConfig;

    /**
     * Endpoints the rate-limit demo is allowed to call.
     * <p>
     * The probe forwards the signed-in user's access token, so the target must never be caller
     * controlled: an arbitrary value would let a user point the server at any host it can reach —
     * internal services or a cloud metadata endpoint — and hand over a valid bearer token with the
     * request. Only these read-only, rate-limited paths are permitted.
     */
    private static final Set<String> PROBE_ENDPOINTS = Set.of(
            "/api/v1/urls",
            "/api/v1/users/me",
            "/api/v1/analytics/top"
    );

    private static final String DEFAULT_PROBE_ENDPOINT = "/api/v1/urls";
    private static final int MAX_PROBE_REQUESTS = 200;

    @GetMapping("/rate-limit")
    public String rateLimit(Model model) {
        model.addAttribute("userRpm", webClientConfig.getRateLimit().getUserRpm());
        model.addAttribute("ipRpm", webClientConfig.getRateLimit().getIpRpm());
        model.addAttribute("probeEndpoints", PROBE_ENDPOINTS.stream().sorted().toList());
        model.addAttribute("pageTitle", "Rate Limit Demo");
        model.addAttribute("activeNav", "tools-rate-limit");
        return "tools/rate-limit";
    }

    @GetMapping("/rate-limit/probe")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rateLimitProbe(
            @RequestParam(defaultValue = "10") int n,
            @RequestParam(defaultValue = DEFAULT_PROBE_ENDPOINT) String endpoint,
            HttpSession session) {
        if (!PROBE_ENDPOINTS.contains(endpoint)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unsupported probe endpoint",
                    "allowed", PROBE_ENDPOINTS.stream().sorted().toList()
            ));
        }

        int count = Math.max(1, Math.min(n, MAX_PROBE_REQUESTS));
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
