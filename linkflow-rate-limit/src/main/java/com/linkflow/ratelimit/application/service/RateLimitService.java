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

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final int WINDOW_SECONDS = 60;
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
        long minuteTimestamp = currentMinuteTimestamp();
        String key = USER_KEY_PREFIX + userId + ":" + minuteTimestamp;
        return check(key, properties.getUserRpm(), minuteTimestamp, false);
    }

    public RateLimitInfo checkForIp(String ipAddress) {
        return checkForIp(ipAddress, false);
    }

    public RateLimitInfo checkForIp(String ipAddress, boolean failClosedOnError) {
        long minuteTimestamp = currentMinuteTimestamp();
        String key = IP_KEY_PREFIX + ipAddress + ":" + minuteTimestamp;
        return check(key, properties.getIpRpm(), minuteTimestamp, failClosedOnError);
    }

    private RateLimitInfo check(String key, int limit, long minuteTimestamp, boolean failClosedOnError) {
        long reset = (minuteTimestamp + 1) * WINDOW_SECONDS;
        try {
            List<Long> result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(limit),
                    String.valueOf(WINDOW_SECONDS)
            );

            if (result == null || result.size() < 3) {
                log.warn("Unexpected rate limit script result for key {}: {}", key, result);
                return failClosedOnError
                        ? RateLimitInfo.failClosed(limit, reset)
                        : RateLimitInfo.failOpen(limit, reset);
            }

            boolean allowed = toLong(result.get(0)) == 1L;
            long current = toLong(result.get(1));
            long remaining = Math.max(0, limit - current);

            return RateLimitInfo.builder()
                    .limit(limit)
                    .remaining(allowed ? remaining : 0)
                    .reset(reset)
                    .allowed(allowed)
                    .build();
        } catch (Exception ex) {
            if (failClosedOnError) {
                log.error("Redis unavailable for rate limiting on key {}, rejecting request (fail-closed)", key, ex);
                return RateLimitInfo.failClosed(limit, reset);
            }
            log.warn("Redis unavailable for rate limiting on key {}, allowing request (fail-open)", key, ex);
            return RateLimitInfo.failOpen(limit, reset);
        }
    }

    private long currentMinuteTimestamp() {
        return Instant.now().getEpochSecond() / WINDOW_SECONDS;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
