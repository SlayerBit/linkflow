package com.linkflow.web.session;

import java.util.Set;

public record AuthState(
        String accessToken,
        String refreshToken,
        long expiresAt,
        String email,
        String firstName,
        String lastName,
        Set<String> roles
) {

    public boolean isAdmin() {
        return roles != null && roles.contains("ADMIN");
    }

    public String displayName() {
        if (firstName != null && !firstName.isBlank()) {
            return firstName + (lastName != null && !lastName.isBlank() ? " " + lastName : "");
        }
        return email;
    }
}
