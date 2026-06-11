package com.linkflow.observability.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component("redisHealthIndicator")
@ConditionalOnBean(RedisConnectionFactory.class)
@RequiredArgsConstructor
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    @Override
    public Health health() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String response = connection.ping();
            if ("PONG".equalsIgnoreCase(response)) {
                return Health.up()
                        .withDetail("response", response)
                        .build();
            }
            return Health.down()
                    .withDetail("response", response)
                    .build();
        } catch (Exception ex) {
            return Health.down(ex).build();
        }
    }
}
