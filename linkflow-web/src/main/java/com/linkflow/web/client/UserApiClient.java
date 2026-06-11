package com.linkflow.web.client;

import com.linkflow.web.dto.common.ApiResponse;
import com.linkflow.web.dto.common.PagedResponse;
import com.linkflow.web.dto.user.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserApiClient {

    private final BackendClient backendClient;

    public UserResponse getMe(String accessToken) {
        var response = backendClient.exchangeForBody(
                backendClient.get("/api/v1/users/me", accessToken),
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {}
        );
        return response.data();
    }

    public UserResponse updateMe(String accessToken, String firstName, String lastName) {
        Map<String, String> body = Map.of(
                "firstName", firstName,
                "lastName", lastName != null ? lastName : ""
        );
        var response = backendClient.exchangeForBody(
                backendClient.put("/api/v1/users/me", accessToken).body(body),
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {}
        );
        return response.data();
    }

    public PagedResponse<UserResponse> listAdminUsers(String accessToken, int page, int size,
                                                      String sortBy, String direction) {
        String uri = backendClient.buildUri(
                "/api/v1/admin/users", "page", page, "size", size, "sortBy", sortBy, "direction", direction
        );
        var response = backendClient.exchangeForBody(
                backendClient.get(uri, accessToken),
                new ParameterizedTypeReference<ApiResponse<PagedResponse<UserResponse>>>() {}
        );
        return response.data();
    }

    public UserResponse getAdminUser(String accessToken, UUID id) {
        var response = backendClient.exchangeForBody(
                backendClient.get("/api/v1/admin/users/" + id, accessToken),
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {}
        );
        return response.data();
    }
}
