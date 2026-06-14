package com.linkflow.url.infrastructure.lock;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Distributed lock service backed by Redis.
 * <p>
 * Lock acquisition uses {@code SET key value NX EX ttl}.
 * Lock release uses an atomic Lua compare-and-delete script to prevent
 * releasing a lock held by a different owner.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLockService {

    private static final String LOCK_PREFIX = "lock:";

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> unlockScript = new DefaultRedisScript<>();

    @PostConstruct
    void loadScripts() {
        unlockScript.setLocation(new ClassPathResource("lua/unlock.lua"));
        unlockScript.setResultType(Long.class);
    }

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

    /**
     * Atomically releases the lock only if the current value matches {@code lockValue}.
     * Uses a Lua script to guarantee that the GET + DEL is a single atomic operation,
     * preventing the race condition where another process acquires the lock between
     * a non-atomic GET and DELETE.
     *
     * @return true if the lock was released, false if not owned or already expired
     */
    public boolean unlock(String lockName, String lockValue) {
        String key = LOCK_PREFIX + lockName;
        try {
            Long result = stringRedisTemplate.execute(
                    unlockScript,
                    Collections.singletonList(key),
                    lockValue
            );
            return result != null && result == 1L;
        } catch (Exception ex) {
            log.warn("Failed to release lock {}: {}", lockName, ex.getMessage());
            return false;
        }
    }
}
