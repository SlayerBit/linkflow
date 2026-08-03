package com.linkflow.app.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Refuses to start the prod profile on infrastructure settings that are safe locally but unsafe
 * when exposed.
 * <p>
 * These are defaults chosen for developer convenience — an unauthenticated Redis, a wildcard CORS
 * origin — and the failure mode if they reach production is silent: the application works
 * perfectly while being open. A startup failure is far cheaper than discovering that later, so all
 * problems are collected and reported at once rather than one redeploy at a time.
 * <p>
 * Runs during context refresh so the process exits before the HTTP connector accepts traffic.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ProductionConfigValidator implements InitializingBean {

    private final Environment environment;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${linkflow.cors.allowed-origins:*}")
    private String corsAllowedOrigins;

    @Value("${linkflow.base-url:}")
    private String baseUrl;

    @Value("${linkflow.trusted-proxies.cidrs:}")
    private String trustedProxies;

    @Override
    public void afterPropertiesSet() {
        if (!isProd()) {
            return;
        }

        List<String> problems = new ArrayList<>();

        if (isBlank(redisPassword)) {
            problems.add("SPRING_DATA_REDIS_PASSWORD is not set. Redis stores sessions, refresh "
                    + "token state, and rate-limit counters; it must require authentication.");
        }

        if (containsWildcardOrigin()) {
            problems.add("LINKFLOW_CORS_ALLOWED_ORIGINS is '*' while credentials are allowed, which "
                    + "lets any site make credentialed cross-origin calls. List explicit origins.");
        }

        if (baseUrl.startsWith("http://")) {
            problems.add("LINKFLOW_BASE_URL must use https in production; short links and email "
                    + "links are built from it.");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid production configuration:"
                    + problems.stream().collect(
                            java.util.stream.Collectors.joining("\n  - ", "\n  - ", "")));
        }

        if (isBlank(trustedProxies)) {
            // Not fatal: running without a proxy is legitimate. But behind one, every client
            // collapses into a single rate-limit bucket, which is worth saying out loud.
            log.warn("No trusted proxies configured. If this instance runs behind a reverse proxy, "
                    + "set LINKFLOW_TRUSTED_PROXIES so per-client rate limiting and click "
                    + "analytics see real client addresses instead of the proxy's.");
        }
    }

    private boolean containsWildcardOrigin() {
        return Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .anyMatch(origin -> origin.equals("*"));
    }

    private boolean isProd() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
