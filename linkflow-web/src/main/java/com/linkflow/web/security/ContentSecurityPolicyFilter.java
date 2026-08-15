package com.linkflow.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Emits a per-request Content-Security-Policy using a nonce for inline scripts.
 * <p>
 * A nonce is used rather than {@code 'unsafe-inline'} because the latter would leave
 * {@code script-src} doing nothing at all: injected markup executes just as readily as the
 * application's own. With a nonce, only the handful of inline blocks the server rendered — which
 * carry the unguessable, per-response value — are allowed to run, so reflected or stored markup
 * that reaches the page is inert.
 * <p>
 * The policy is written here rather than through Spring Security's header writer because that
 * writer takes a fixed string, and a nonce that does not change per response is no better than
 * {@code 'unsafe-inline'}.
 * <p>
 * {@code style-src} still permits inline styles: the templates carry many {@code style="..."}
 * attributes, and nonces do not apply to attributes in any case. Scripts are where the protection
 * matters.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ContentSecurityPolicyFilter extends OncePerRequestFilter {

    /** Request attribute holding the nonce, read by templates when rendering inline scripts. */
    public static final String NONCE_ATTRIBUTE = "cspNonce";

    private static final int NONCE_BYTES = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String nonce = generateNonce();
        request.setAttribute(NONCE_ATTRIBUTE, nonce);
        response.setHeader("Content-Security-Policy", buildPolicy(nonce));

        filterChain.doFilter(request, response);
    }

    private String generateNonce() {
        byte[] bytes = new byte[NONCE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String buildPolicy(String nonce) {
        return String.join("; ",
                "default-src 'self'",
                "script-src 'self' 'nonce-" + nonce + "'",
                // Inline style attributes remain; nonces do not apply to them.
                "style-src 'self' 'unsafe-inline'",
                "font-src 'self' data:",
                "img-src 'self' data:",
                "connect-src 'self'",
                // No part of the app is meant to be framed, and no plugin content is used.
                "frame-ancestors 'none'",
                "object-src 'none'",
                // Stops injected markup from retargeting relative URLs or posting credentials away.
                "base-uri 'self'",
                "form-action 'self'");
    }
}
