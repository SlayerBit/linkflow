package com.linkflow.web.client;

import com.linkflow.web.dto.common.ApiResponse;
import com.linkflow.web.dto.common.PagedResponse;
import com.linkflow.web.dto.url.UrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UrlApiClient {

    private final BackendClient backendClient;

    public UrlResponse create(String accessToken, String originalUrl, String customAlias,
                              Instant expiresAt, String idempotencyKey) {
        Map<String, Object> body = new HashMap<>();
        body.put("originalUrl", originalUrl);
        if (customAlias != null && !customAlias.isBlank()) {
            body.put("customAlias", customAlias);
        }
        if (expiresAt != null) {
            body.put("expiresAt", expiresAt.toString());
        }

        var spec = backendClient.post("/api/v1/urls", accessToken).body(body);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            spec = spec.header("Idempotency-Key", idempotencyKey);
        }

        var response = backendClient.exchangeForBody(
                spec,
                new ParameterizedTypeReference<ApiResponse<UrlResponse>>() {}
        );
        return response.data();
    }

    public PagedResponse<UrlResponse> listUserUrls(String accessToken, int page, int size,
                                                   String sortBy, String direction) {
        String uri = backendClient.buildUri(
                "/api/v1/urls", "page", page, "size", size, "sortBy", sortBy, "direction", direction
        );
        var response = backendClient.exchangeForBody(
                backendClient.get(uri, accessToken),
                new ParameterizedTypeReference<ApiResponse<PagedResponse<UrlResponse>>>() {}
        );
        return response.data();
    }

    public UrlResponse getById(String accessToken, UUID id) {
        var response = backendClient.exchangeForBody(
                backendClient.get("/api/v1/urls/" + id, accessToken),
                new ParameterizedTypeReference<ApiResponse<UrlResponse>>() {}
        );
        return response.data();
    }

    public UrlResponse update(String accessToken, UUID id, Instant expiresAt, Boolean active) {
        Map<String, Object> body = new HashMap<>();
        if (expiresAt != null) {
            body.put("expiresAt", expiresAt.toString());
        }
        if (active != null) {
            body.put("active", active);
        }

        var response = backendClient.exchangeForBody(
                backendClient.patch("/api/v1/urls/" + id, accessToken).body(body),
                new ParameterizedTypeReference<ApiResponse<UrlResponse>>() {}
        );
        return response.data();
    }

    public void delete(String accessToken, UUID id) {
        backendClient.exchangeForBody(
                backendClient.delete("/api/v1/urls/" + id, accessToken),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );
    }

    public byte[] getQrCode(String accessToken, UUID id) {
        return backendClient.exchangeForBytes(
                backendClient.get("/api/v1/urls/" + id + "/qr", accessToken)
        );
    }

    public PagedResponse<UrlResponse> listAdminUrls(String accessToken, int page, int size,
                                                    String sortBy, String direction) {
        String uri = backendClient.buildUri(
                "/api/v1/admin/urls", "page", page, "size", size, "sortBy", sortBy, "direction", direction
        );
        var response = backendClient.exchangeForBody(
                backendClient.get(uri, accessToken),
                new ParameterizedTypeReference<ApiResponse<PagedResponse<UrlResponse>>>() {}
        );
        return response.data();
    }

    public UrlResponse adminDeactivate(String accessToken, UUID id) {
        var response = backendClient.exchangeForBody(
                backendClient.patch("/api/v1/admin/urls/" + id + "/deactivate", accessToken).body(Map.of()),
                new ParameterizedTypeReference<ApiResponse<UrlResponse>>() {}
        );
        return response.data();
    }

    public UrlResponse adminReactivate(String accessToken, UUID id) {
        var response = backendClient.exchangeForBody(
                backendClient.patch("/api/v1/admin/urls/" + id + "/reactivate", accessToken).body(Map.of()),
                new ParameterizedTypeReference<ApiResponse<UrlResponse>>() {}
        );
        return response.data();
    }

    public UrlResponse reactivate(String accessToken, UUID id) {
        var response = backendClient.exchangeForBody(
                backendClient.patch("/api/v1/urls/" + id + "/reactivate", accessToken).body(Map.of()),
                new ParameterizedTypeReference<ApiResponse<UrlResponse>>() {}
        );
        return response.data();
    }
}
