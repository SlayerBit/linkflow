package com.linkflow.ratelimit.infrastructure.filter;

import com.linkflow.common.exception.RateLimitBackendUnavailableException;
import com.linkflow.common.exception.RateLimitExceededException;
import com.linkflow.common.metrics.LinkflowMetrics;
import com.linkflow.common.security.ClientIpResolver;
import com.linkflow.common.security.UserPrincipal;
import com.linkflow.ratelimit.api.dto.RateLimitInfo;
import com.linkflow.ratelimit.application.service.RateLimitService;
import com.linkflow.ratelimit.infrastructure.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;

@Component("rateLimitFilter")
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String HEADER_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    public static final String HEADER_RESET = "X-RateLimit-Reset";

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";

    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final ClientIpResolver clientIpResolver;
    private final LinkflowMetrics metrics;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ResolvedLimit resolved = resolveRateLimitInfo(request);
        RateLimitInfo info = resolved.info();
        addRateLimitHeaders(response, info);

        if (info.isBackendUnavailable()) {
            metrics.rateLimitBackendUnavailable(resolved.dimension());
            handleException(request, response, new RateLimitBackendUnavailableException());
            return;
        }

        if (!info.isAllowed()) {
            metrics.rateLimitExceeded(resolved.dimension());
            handleException(request, response,
                    new RateLimitExceededException("Too many requests. Please try again later."));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private ResolvedLimit resolveRateLimitInfo(HttpServletRequest request) {
        boolean failClosed = isAuthPath(request.getRequestURI()) && rateLimitProperties.isAuthFailClosed();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return new ResolvedLimit(rateLimitService.checkForUser(principal.getId()), "user");
        }
        return new ResolvedLimit(
                rateLimitService.checkForIp(clientIpResolver.resolve(request), failClosed), "ip");
    }

    private record ResolvedLimit(RateLimitInfo info, String dimension) {
    }

    private boolean isAuthPath(String path) {
        return path.startsWith(AUTH_PATH_PREFIX);
    }

    private void handleException(HttpServletRequest request,
                                 HttpServletResponse response,
                                 RuntimeException ex) {
        ModelAndView resolved = handlerExceptionResolver.resolveException(request, response, null, ex);
        if (resolved == null) {
            throw ex;
        }
    }

    private void addRateLimitHeaders(HttpServletResponse response, RateLimitInfo info) {
        response.setHeader(HEADER_LIMIT, String.valueOf(info.getLimit()));
        response.setHeader(HEADER_REMAINING, String.valueOf(info.getRemaining()));
        response.setHeader(HEADER_RESET, String.valueOf(info.getReset()));
    }

}
