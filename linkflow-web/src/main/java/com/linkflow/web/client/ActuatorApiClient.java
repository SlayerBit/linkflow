package com.linkflow.web.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ActuatorApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public JsonNode getHealth() {
        String body = restClient.get()
                .uri("/actuator/health")
                .retrieve()
                .body(String.class);
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse health response", e);
        }
    }

    public List<RateLimitProbeResult> probe(String accessToken, String path, int count) {
        List<RateLimitProbeResult> results = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            results.add(probeOnce(accessToken, path, i));
        }
        return results;
    }

    private RateLimitProbeResult probeOnce(String accessToken, String path, int requestNumber) {
        var spec = restClient.get().uri(path);
        if (accessToken != null && !accessToken.isBlank()) {
            spec = spec.header("Authorization", "Bearer " + accessToken);
        }

        return spec.exchange((req, res) -> toProbeResult(requestNumber, res));
    }

    private RateLimitProbeResult toProbeResult(int requestNumber, ClientHttpResponse response)
            throws IOException {
        int status = response.getStatusCode().value();
        String limit = response.getHeaders().getFirst("X-RateLimit-Limit");
        String remaining = response.getHeaders().getFirst("X-RateLimit-Remaining");
        String reset = response.getHeaders().getFirst("X-RateLimit-Reset");
        String message = status >= 400 ? extractErrorMessage(response) : null;
        return new RateLimitProbeResult(requestNumber, status, limit, remaining, reset, message);
    }

    private String extractErrorMessage(ClientHttpResponse response) {
        try {
            String body = new String(response.getBody().readAllBytes());
            JsonNode node = objectMapper.readTree(body);
            if (node.has("message")) {
                return node.get("message").asText();
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    public record RateLimitProbeResult(
            int requestNumber,
            int status,
            String limit,
            String remaining,
            String reset,
            String message
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("requestNumber", requestNumber);
            map.put("status", status);
            map.put("limit", limit);
            map.put("remaining", remaining);
            map.put("reset", reset);
            map.put("message", message);
            return map;
        }
    }
}
