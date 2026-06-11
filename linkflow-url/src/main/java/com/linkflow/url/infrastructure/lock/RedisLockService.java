package com.linkflow.url.infrastructure.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLockService {

    private static final String LOCK_PREFIX = "lock:";

    private final StringRedisTemplate stringRedisTemplate;

    public boolean tryLock(String lockName, String lockValue, Duration ttl) {
        String key = LOCK_PREFIX + lockName;
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, lockValue, ttl.toMillis(), TimeUnit.MILLISECONDS);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception ex) {
            log.warn("Failed to acquire lock {}: {}", lockName, ex.getMessage());
            return false;
        }
    }

    public void unlock(String lockName, String lockValue) {
        String key = LOCK_PREFIX + lockName;
        try {
            String current = stringRedisTemplate.opsForValue().get(key);
            if (lockValue.equals(current)) {
                stringRedisTemplate.delete(key);
            }
        } catch (Exception ex) {
            log.warn("Failed to release lock {}: {}", lockName, ex.getMessage());
        }
    }
}
