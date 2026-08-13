package io.github.guanxiangkai.redis.plus.core.redis;

import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

/**
 * Minimal Redis command gateway used by redis-plus modules.
 */
public interface RedisBackend {

    String get(String key);

    void set(String key, String value, Duration ttl);

    Boolean delete(String key);

    Long getExpireMillis(String key);

    <T> T execute(RedisScript<T> script, List<String> keys, Object... args);
}
