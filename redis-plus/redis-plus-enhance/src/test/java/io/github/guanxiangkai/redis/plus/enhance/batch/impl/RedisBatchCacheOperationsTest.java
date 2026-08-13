package io.github.guanxiangkai.redis.plus.enhance.batch.impl;

import io.github.guanxiangkai.redis.plus.cache.CacheRuntimePolicy;
import io.github.guanxiangkai.redis.plus.cache.ThreeLevelCacheTemplate;
import io.github.guanxiangkai.redis.plus.cache.spi.CacheConsistencyStrategy;
import io.github.guanxiangkai.redis.plus.cache.spi.CacheLoadProtection;
import io.github.guanxiangkai.redis.plus.cache.spi.CacheMetricsCollector;
import io.github.guanxiangkai.redis.plus.cache.spi.LocalCacheProvider;
import io.github.guanxiangkai.redis.plus.core.expire.ExpireStrategy;
import io.github.guanxiangkai.redis.plus.core.key.DefaultKeyNamingStrategy;
import io.github.guanxiangkai.redis.plus.core.observation.RedisPlusObserver;
import io.github.guanxiangkai.redis.plus.core.serializer.ValueSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedisBatchCacheOperations} 运行策略测试。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@SuppressWarnings("NullAway")
class RedisBatchCacheOperationsTest {

    private LocalCacheProvider l1;
    private ValueOperations<String, String> valueOperations;
    private RedisBatchCacheOperations operations;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        l1 = mock(LocalCacheProvider.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ValueSerializer serializer = mock(ValueSerializer.class);
        when(serializer.deserialize(anyString(), eq(String.class))).thenReturn("alice");
        CacheRuntimePolicy policy = new CacheRuntimePolicy(
                Duration.ofSeconds(3), Duration.ofSeconds(9), 20, 30, 0.5, Duration.ofSeconds(8),
                Duration.ofSeconds(45), Duration.ofSeconds(12));
        ThreeLevelCacheTemplate template = new ThreeLevelCacheTemplate(
                l1, redisTemplate, serializer, CacheLoadProtection.noProtection(), ExpireStrategy.fixed(),
                CacheMetricsCollector.noop(), "", CacheConsistencyStrategy.invalidate(), new DefaultKeyNamingStrategy(),
                RedisPlusObserver.noop(), policy);
        operations = new RedisBatchCacheOperations(template, l1, redisTemplate, serializer, policy);
    }

    @Test
    void multiGet_l2ValueUsesConfiguredBatchLocalCacheTtl() {
        when(valueOperations.multiGet(List.of("user:1"))).thenReturn(List.of("alice"));

        operations.multiGet("user", List.of("1"), String.class);

        verify(l1).put(eq("user:1"), any(), eq(Duration.ofSeconds(45)));
    }

    @Test
    void multiGet_l2NullValueUsesConfiguredBatchNullCacheTtl() {
        when(valueOperations.multiGet(List.of("user:1"))).thenReturn(List.of("__REDIS_PLUS_NULL__"));

        var result = operations.multiGet("user", List.of("1"), String.class);

        assertNull(result.get("1"));
        verify(l1).put(eq("user:1"), any(), eq(Duration.ofSeconds(12)));
    }
}
