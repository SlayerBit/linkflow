package com.linkflow.web.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkflow.web.dto.common.ApiErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class BackendClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RestClient.RequestHeadersSpec<?> get(String path, String accessToken) {
        return restClient.get()
                .uri(path)
                .headers(headers -> applyAuth(headers, accessToken));
    }

    public RestClient.RequestBodySpec post(String path, String accessToken) {
        return restClient.post()
                .uri(path)
                .headers(headers -> applyAuth(headers, accessToken))
                .contentType(MediaType.APPLICATION_JSON);
    }

    public RestClient.RequestBodySpec put(String path, String accessToken) {
        return restClient.put()
                .uri(path)
                .headers(headers -> applyAuth(headers, accessToken))
                .contentType(MediaType.APPLICATION_JSON);
    }

    public RestClient.RequestBodySpec patch(String path, String accessToken) {
        return restClient.patch()
                .uri(path)
                .headers(headers -> applyAuth(headers, accessToken))
                .contentType(MediaType.APPLICATION_JSON);
    }

    public RestClient.RequestHeadersSpec<?> delete(String path, String accessToken) {
        return restClient.delete()
                .uri(path)
                .headers(headers -> applyAuth(headers, accessToken));
    }

    public RestClient.RequestBodySpec postPublic(String path) {
        return restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON);
    }

    public String buildUri(String path, Object... queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        for (int i = 0; i < queryParams.length; i += 2) {
            builder.queryParam(String.valueOf(queryParams[i]), queryParams[i + 1]);
        }
        return builder.build().toUriString();
    }

    public <T> T exchangeForBody(RestClient.RequestHeadersSpec<?> spec,
                                 ParameterizedTypeReference<T> type) {
        try {
            return spec.retrieve().body(type);
        } catch (HttpStatusCodeException ex) {
            throw toApiException(ex);
        }
    }

    public byte[] exchangeForBytes(RestClient.RequestHeadersSpec<?> spec) {
        try {
            return spec.retrieve().body(byte[].class);
        } catch (HttpStatusCodeException ex) {
            throw toApiException(ex);
        }
    }

    /**
     * {@code HttpStatusCodeException} is the common supertype of the client and server error
     * exceptions, so this one overload covers both.
     */
    public BackendApiException toApiException(HttpStatusCodeException ex) {
        return parseError(ex.getResponseBodyAsString(), ex.getStatusCode().value());
    }

    private BackendApiException parseError(String body, int status) {
        try {
            ApiErrorResponse error = objectMapper.readValue(body, ApiErrorResponse.class);
            return new BackendApiException(
                    error.message() != null ? error.message() : "Request failed",
                    error.errorCode(),
                    status
            );
        } catch (Exception e) {
            return new BackendApiException("Request failed with status " + status, null, status);
        }
    }

    private void applyAuth(HttpHeaders headers, String accessToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            headers.setBearerAuth(accessToken);
        }
    }
}
