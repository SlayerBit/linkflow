package com.linkflow.analytics.infrastructure.redis;

import com.linkflow.analytics.infrastructure.config.AnalyticsRedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Atomically reads and subtracts a pending click counter so multi-instance flushes
 * cannot double-count.
 */
@Component
@RequiredArgsConstructor
public class AnalyticsCounterFlushScript {

    private static final DefaultRedisScript<List> FLUSH_SCRIPT = new DefaultRedisScript<>(
            """
            local val = redis.call('HGET', KEYS[1], ARGV[1])
            if not val then return {0, 0} end
            local count = tonumber(val)
            if count <= 0 then return {0, 0} end
            redis.call('HINCRBY', KEYS[1], ARGV[1], -count)
            local remaining = tonumber(redis.call('HGET', KEYS[1], ARGV[1]) or '0')
            return {count, remaining}
            """,
            List.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    public CounterFlushResult flushCounter(String shortUrlIdStr) {
        String counterKey = AnalyticsRedisConstants.COUNTER_KEY_PREFIX + shortUrlIdStr;
        List<?> result = stringRedisTemplate.execute(
                FLUSH_SCRIPT,
                List.of(counterKey),
                AnalyticsRedisConstants.COUNTER_FIELD_TOTAL
        );
        if (result == null || result.size() < 2) {
            return new CounterFlushResult(0, 0);
        }
        long flushed = Long.parseLong(String.valueOf(result.get(0)));
        long remaining = Long.parseLong(String.valueOf(result.get(1)));
        return new CounterFlushResult(flushed, remaining);
    }

    public record CounterFlushResult(long flushedCount, long remainingCount) {}
}
