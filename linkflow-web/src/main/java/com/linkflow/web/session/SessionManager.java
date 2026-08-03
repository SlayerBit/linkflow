package com.linkflow.web.session;

import jakarta.servlet.http.HttpServletRequest;
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

    /**
     * Binds a freshly authenticated user to the current session, rotating the session identifier.
     * <p>
     * Rotation closes session fixation: without it, an identifier an attacker managed to plant in
     * the victim's browser before login would still be valid afterwards, handing them an
     * authenticated session. {@code changeSessionId()} issues a new identifier while preserving
     * attributes, so it is safe to call before writing the auth state.
     */
    public void establishSession(HttpServletRequest request, String accessToken, String refreshToken,
                                 long expiresIn, String email, String firstName, String lastName,
                                 Set<String> roles) {
        request.changeSessionId();

        HttpSession session = request.getSession();
        session.setAttribute(SessionKeys.AUTH_STATE,
                buildAuthState(accessToken, refreshToken, expiresIn, email, firstName, lastName, roles));

        var authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
        var authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Refreshes the cached profile fields of an already-authenticated session. Unlike
     * {@link #establishSession} this is not a privilege transition, so the session id is retained.
     */
    public void updateUserDetails(HttpSession session, AuthState current, String email,
                                  String firstName, String lastName, Set<String> roles) {
        long remainingSeconds = Math.max(0, current.expiresAt() - Instant.now().getEpochSecond());
        session.setAttribute(SessionKeys.AUTH_STATE, buildAuthState(
                current.accessToken(), current.refreshToken(), remainingSeconds,
                email, firstName, lastName, roles));
    }

    public void updateTokens(HttpSession session, AuthState current, String accessToken,
                             String refreshToken, long expiresIn) {
        session.setAttribute(SessionKeys.AUTH_STATE, buildAuthState(
                accessToken, refreshToken, expiresIn,
                current.email(), current.firstName(), current.lastName(), current.roles()));
    }

    public void clearSession(HttpSession session) {
        session.removeAttribute(SessionKeys.AUTH_STATE);
        SecurityContextHolder.clearContext();
        session.invalidate();
    }

    private AuthState buildAuthState(String accessToken, String refreshToken, long expiresIn,
                                     String email, String firstName, String lastName,
                                     Set<String> roles) {
        long expiresAt = Instant.now().getEpochSecond() + expiresIn;
        return new AuthState(accessToken, refreshToken, expiresAt, email, firstName, lastName, roles);
    }
}
