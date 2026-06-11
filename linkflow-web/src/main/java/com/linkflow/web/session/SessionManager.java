package com.linkflow.web.session;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SessionManager {

    public AuthState getAuthState(HttpSession session) {
        Object value = session.getAttribute(SessionKeys.AUTH_STATE);
        if (value instanceof AuthState authState) {
            return authState;
        }
        return null;
    }

    public void establishSession(HttpSession session, String accessToken, String refreshToken,
                                 long expiresIn, String email, String firstName, String lastName,
                                 Set<String> roles) {
        long expiresAt = Instant.now().getEpochSecond() + expiresIn;
        AuthState authState = new AuthState(
                accessToken, refreshToken, expiresAt, email, firstName, lastName, roles
        );
        session.setAttribute(SessionKeys.AUTH_STATE, authState);

        var authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
        var authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    public void updateTokens(HttpSession session, AuthState current, String accessToken,
                             String refreshToken, long expiresIn) {
        long expiresAt = Instant.now().getEpochSecond() + expiresIn;
        AuthState updated = new AuthState(
                accessToken,
                refreshToken,
                expiresAt,
                current.email(),
                current.firstName(),
                current.lastName(),
                current.roles()
        );
        session.setAttribute(SessionKeys.AUTH_STATE, updated);
    }

    public void clearSession(HttpSession session) {
        session.removeAttribute(SessionKeys.AUTH_STATE);
        SecurityContextHolder.clearContext();
        session.invalidate();
    }
}
