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

    public void requestEmailChange(String accessToken, String currentPassword, String newEmail) {
        Map<String, String> body = Map.of("currentPassword", currentPassword, "newEmail", newEmail);
        backendClient.exchangeForBody(
                backendClient.post("/api/v1/users/me/email-change-request", accessToken).body(body),
                new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {}
        );
    }

    public void verifyEmailChange(String token) {
        Map<String, String> body = Map.of("token", token);
        backendClient.exchangeForBody(
                backendClient.postPublic("/api/v1/users/verify-email-change").body(body),
                new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {}
        );
    }

    public UserResponse disableUser(String accessToken, UUID id) {
        var response = backendClient.exchangeForBody(
                backendClient.patch("/api/v1/admin/users/" + id + "/disable", accessToken).body(Map.of()),
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {}
        );
        return response.data();
    }

    public UserResponse enableUser(String accessToken, UUID id) {
        var response = backendClient.exchangeForBody(
                backendClient.patch("/api/v1/admin/users/" + id + "/enable", accessToken).body(Map.of()),
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {}
        );
        return response.data();
    }

    public void deleteUser(String accessToken, UUID id) {
        backendClient.exchangeForBody(
                backendClient.delete("/api/v1/admin/users/" + id, accessToken),
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {}
        );
    }

    public UserResponse updateUserRoles(String accessToken, UUID id, java.util.Set<String> roles) {
        Map<String, Object> body = Map.of("roles", roles);
        var response = backendClient.exchangeForBody(
                backendClient.patch("/api/v1/admin/users/" + id + "/roles", accessToken).body(body),
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {}
        );
        return response.data();
    }
}
