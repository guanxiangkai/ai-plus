package io.github.guanxiangkai.redis.plus.core.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Spring Data Redis implementation of {@link RedisBackend}.
 */
public class StringRedisBackend implements RedisBackend {

    private final StringRedisTemplate redisTemplate;

    public StringRedisBackend(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
    }

    @Override
    @SuppressWarnings("NullAway")
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    @SuppressWarnings("NullAway")
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    @Override
    @SuppressWarnings("NullAway")
    public Long getExpireMillis(String key) {
        return redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
    }

    @Override
    @SuppressWarnings("NullAway")
    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
        return redisTemplate.execute(script, keys, args);
    }
}
