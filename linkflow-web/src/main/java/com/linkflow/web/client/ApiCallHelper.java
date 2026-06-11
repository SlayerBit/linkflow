package com.linkflow.web.client;

import com.linkflow.web.dto.auth.TokenResponse;
import com.linkflow.web.session.AuthState;
import com.linkflow.web.session.SessionManager;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class ApiCallHelper {

    private final AuthApiClient authApiClient;
    private final SessionManager sessionManager;

    public <T> T withTokenRefresh(HttpSession session, Function<AuthState, T> call) {
        AuthState authState = sessionManager.getAuthState(session);
        if (authState == null) {
            throw new SessionExpiredException();
        }

        try {
            return call.apply(authState);
        } catch (BackendApiException ex) {
            if (ex.getStatusCode() != 401) {
                throw ex;
            }
            TokenResponse refreshed = authApiClient.refresh(authState.refreshToken());
            sessionManager.updateTokens(
                    session,
                    authState,
                    refreshed.accessToken(),
                    refreshed.refreshToken(),
                    refreshed.expiresIn()
            );
            AuthState updated = sessionManager.getAuthState(session);
            try {
                return call.apply(updated);
            } catch (BackendApiException retryEx) {
                if (retryEx.getStatusCode() == 401) {
                    sessionManager.clearSession(session);
                    throw new SessionExpiredException();
                }
                throw retryEx;
            }
        }
    }

    public AuthState requireAuth(HttpSession session) {
        AuthState authState = sessionManager.getAuthState(session);
        if (authState == null) {
            throw new SessionExpiredException();
        }
        return authState;
    }
}
