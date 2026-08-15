package com.linkflow.web.controller;

import com.linkflow.web.dto.analytics.UrlAnalyticsResponse;
import com.linkflow.web.dto.common.PagedResponse;
import com.linkflow.web.dto.url.UrlResponse;
import com.linkflow.web.dto.user.UserResponse;
import com.linkflow.web.session.AuthState;
import com.linkflow.web.session.SessionKeys;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class PageRenderFixtures {

    static final UUID URL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    static final AuthState USER_AUTH = new AuthState(
            "access-token",
            "refresh-token",
            System.currentTimeMillis() + 3_600_000,
            "ada@example.com",
            "Ada",
            "Lovelace",
            Set.of("USER")
    );

    static final AuthState ADMIN_AUTH = new AuthState(
            "access-token",
            "refresh-token",
            System.currentTimeMillis() + 3_600_000,
            "admin@example.com",
            "Ada",
            "Admin",
            Set.of("USER", "ADMIN")
    );

    private PageRenderFixtures() {
    }

    static MockHttpSession userSession() {
        return sessionWith(USER_AUTH);
    }

    static MockHttpSession adminSession() {
        return sessionWith(ADMIN_AUTH);
    }

    static MockHttpSession sessionWith(AuthState auth) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.AUTH_STATE, auth);
        return session;
    }

    static UrlResponse url() {
        return new UrlResponse(
                URL_ID,
                "abc12",
                "https://lf.test/abc12",
                "https://example.com/page",
                null,
                true,
                Instant.parse("2026-01-15T10:00:00Z")
        );
    }

    static UrlAnalyticsResponse analytics() {
        return new UrlAnalyticsResponse(
                URL_ID,
                "abc12",
                3,
                Instant.parse("2026-01-16T10:00:00Z")
        );
    }

    static UserResponse user() {
        return new UserResponse(
                USER_ID,
                "ada@example.com",
                "Ada",
                "Lovelace",
                Set.of("USER"),
                true,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
    }

    static <T> PagedResponse<T> emptyPage() {
        return new PagedResponse<>(List.of(), 0, 20, 0, 0);
    }
}
