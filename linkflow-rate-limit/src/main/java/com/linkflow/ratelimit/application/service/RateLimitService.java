package com.linkflow.ratelimit.application.service;

import com.linkflow.ratelimit.api.dto.RateLimitInfo;
import com.linkflow.ratelimit.infrastructure.config.RateLimitProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Sliding-window rate limiter backed by Redis sorted sets.
 * <p>
 * Each request is stored as a member in a sorted set with score = current timestamp
 * in microseconds. Before counting, entries outside the sliding window are pruned.
 * This eliminates the boundary-burst problem of fixed-window counters.
 * <p>
 * Failure modes:
 * <ul>
 *   <li>Redis unavailable + fail-open (default): request allowed, logged as warning</li>
 *   <li>Redis unavailable + fail-closed (auth paths): request denied with 503</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final long WINDOW_SECONDS = 60;
    private static final long WINDOW_MICROS = WINDOW_SECONDS * 1_000_000;
    private static final String USER_KEY_PREFIX = "rate_limit:user:";
    private static final String IP_KEY_PREFIX = "rate_limit:ip:";

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final DefaultRedisScript<List<Long>> rateLimitScript = new DefaultRedisScript<>();

    @PostConstruct
    void loadScript() {
        rateLimitScript.setLocation(new ClassPathResource("lua/rate_limiter.lua"));
        rateLimitScript.setResultType(listResultType());
    }

    @SuppressWarnings("unchecked")
    private Class<List<Long>> listResultType() {
        return (Class<List<Long>>) (Class<?>) List.class;
    }

    public RateLimitInfo checkForUser(UUID userId) {
        String key = USER_KEY_PREFIX + userId;
        return check(key, properties.getUserRpm(), false);
    }

    public RateLimitInfo checkForIp(String ipAddress, boolean failClosedOnError) {
        String key = IP_KEY_PREFIX + ipAddress;
        return check(key, properties.getIpRpm(), failClosedOnError);
    }

    private RateLimitInfo check(String key, int limit, boolean failClosedOnError) {
        long nowMicros = Instant.now().toEpochMilli() * 1000;
        long resetEpoch = (nowMicros + WINDOW_MICROS) / 1_000_000;
        String memberId = UUID.randomUUID().toString();

        try {
            List<Long> result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(limit),
                    String.valueOf(WINDOW_MICROS),
                    String.valueOf(nowMicros),
                    memberId
            );

            if (result == null || result.size() < 3) {
                log.warn("Unexpected rate limit script result for key {}: {}", key, result);
                return failClosedOnError
                        ? RateLimitInfo.failClosed(limit, resetEpoch)
                        : RateLimitInfo.failOpen(limit, resetEpoch);
            }

            boolean allowed = toLong(result.get(0)) == 1L;
            long current = toLong(result.get(1));
            long remaining = Math.max(0, limit - current);

            return RateLimitInfo.builder()
                    .limit(limit)
                    .remaining(allowed ? remaining : 0)
                    .reset(resetEpoch)
                    .allowed(allowed)
                    .build();
        } catch (Exception ex) {
            if (failClosedOnError) {
                log.error("Redis unavailable for rate limiting on key {}, rejecting request (fail-closed)", key, ex);
                return RateLimitInfo.failClosed(limit, resetEpoch);
            }
            log.warn("Redis unavailable for rate limiting on key {}, allowing request (fail-open)", key, ex);
            return RateLimitInfo.failOpen(limit, resetEpoch);
        }
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
