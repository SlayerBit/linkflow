package com.linkflow.ratelimit.application.service;

import com.linkflow.ratelimit.api.dto.RateLimitInfo;
import com.linkflow.ratelimit.infrastructure.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setUserRpm(5);
        properties.setIpRpm(10);
        rateLimitService = new RateLimitService(redisTemplate, properties);
        rateLimitService.loadScript();
    }

    @Test
    void checkForUser_allowsWhenUnderLimit() {
        // Lua script returns: [allowed=1, current=2, ttl=60]
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(1L, 2L, 60L));

        RateLimitInfo info = rateLimitService.checkForUser(UUID.randomUUID());

        assertTrue(info.isAllowed());
        assertEquals(5, info.getLimit());
        assertEquals(3, info.getRemaining());
    }

    @Test
    void checkForUser_deniesWhenOverLimit() {
        // Lua script returns: [allowed=0, current=6, ttl=45]
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(0L, 6L, 45L));

        RateLimitInfo info = rateLimitService.checkForUser(UUID.randomUUID());

        assertFalse(info.isAllowed());
        assertEquals(0, info.getRemaining());
    }

    @Test
    void checkForIp_failOpenWhenRedisUnavailable() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Redis down"));

        RateLimitInfo info = rateLimitService.checkForIp("192.168.1.1");

        assertTrue(info.isAllowed());
        assertEquals(10, info.getLimit());
    }

    @Test
    void checkForIp_failClosedWhenRedisUnavailable() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Redis down"));

        RateLimitInfo info = rateLimitService.checkForIp("192.168.1.1", true);

        assertFalse(info.isAllowed());
        assertTrue(info.isBackendUnavailable());
    }

    @Test
    void checkForUser_handlesNullScriptResult() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(null);

        RateLimitInfo info = rateLimitService.checkForUser(UUID.randomUUID());

        // Default fail-open for user checks
        assertTrue(info.isAllowed());
    }

    @Test
    void checkForIp_handlesShortScriptResult() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(1L));

        RateLimitInfo info = rateLimitService.checkForIp("10.0.0.1");

        // Unexpected result treated as fail-open
        assertTrue(info.isAllowed());
    }
}
