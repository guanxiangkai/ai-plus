package io.github.guanxiangkai.redis.plus.cache;

import io.github.guanxiangkai.redis.plus.cache.spi.CacheLoadProtection;
import io.github.guanxiangkai.redis.plus.cache.spi.CacheMetricsCollector;
import io.github.guanxiangkai.redis.plus.core.serializer.ValueSerializer;
import io.github.guanxiangkai.redis.plus.cache.spi.LocalCacheProvider;
import io.github.guanxiangkai.redis.plus.core.expire.ExpireStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link ThreeLevelCacheTemplate} 单元测试（mock-based，无需 Redis）
 */
@SuppressWarnings("NullAway")
class ThreeLevelCacheTemplateTest {

    private LocalCacheProvider l1;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ValueSerializer serializer;
    private ThreeLevelCacheTemplate template;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        l1 = new SimpleLocalCache();
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        serializer = new ValueSerializer() {
            @Override
            public String serialize(Object value) {
                return String.valueOf(value);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T deserialize(String data, Class<T> type) {
                return (T) data;
            }
        };

        template = new ThreeLevelCacheTemplate(
                l1, redisTemplate, serializer,
                CacheLoadProtection.noProtection(),
                ExpireStrategy.fixed(),
                CacheMetricsCollector.noop(),
                "",
                io.github.guanxiangkai.redis.plus.cache.spi.CacheConsistencyStrategy.invalidate(),
                new io.github.guanxiangkai.redis.plus.core.key.DefaultKeyNamingStrategy(),
                io.github.guanxiangkai.redis.plus.core.observation.RedisPlusObserver.noop(),
                CacheRuntimePolicy.defaults()
        );
    }

    @Test
    void get_l1Hit_returnsDirectly() {
        l1.put("user:1", "Alice", Duration.ofMinutes(5));

        String result = template.get("user", "1", String.class, Duration.ofMinutes(5), k -> "fromDB");

        assertEquals("Alice", result);
        // L2 should not be queried
        verify(valueOps, never()).get(anyString());
    }

    @Test
    void get_l2Hit_backfillsL1() {
        when(valueOps.get("user:1")).thenReturn("Bob");
        when(redisTemplate.getExpire("user:1", java.util.concurrent.TimeUnit.MILLISECONDS)).thenReturn(Duration.ofMinutes(4).toMillis());

        String result = template.get("user", "1", String.class, Duration.ofMinutes(5), k -> "fromDB");

        assertEquals("Bob", result);
        // L1 should now have the value
        assertEquals("Bob", l1.get("user:1"));
        assertEquals(Duration.ofMinutes(4), ((SimpleLocalCache) l1).ttlOf("user:1"));
    }

    @Test
    void get_l2NullValue_returnsNullAndCachesMarker() {
        when(valueOps.get("user:1")).thenReturn("__REDIS_PLUS_NULL__");

        String result = template.get("user", "1", String.class, Duration.ofMinutes(5), k -> "fromDB");

        assertNull(result);
        // L1 should have NULL_MARKER
        assertNotNull(l1.get("user:1"));
    }

    @Test
    void get_miss_loadsFromL3() {
        when(valueOps.get("user:1")).thenReturn(null);

        String result = template.get("user", "1", String.class, Duration.ofMinutes(5), k -> "fromDB");

        assertEquals("fromDB", result);
        // Should write to L2
        verify(valueOps).set(eq("user:1"), eq("fromDB"), any(Duration.class));
        // Should write to L1
        assertEquals("fromDB", l1.get("user:1"));
        assertEquals(Duration.ofMinutes(5), ((SimpleLocalCache) l1).ttlOf("user:1"));
    }

    @Test
    void get_miss_usesShorterLocalTtlWhenConfigured() {
        when(valueOps.get("user:1")).thenReturn(null);

        String result = template.get("user", "1", String.class,
                Duration.ofMinutes(5), Duration.ofMinutes(1), k -> "fromDB");

        assertEquals("fromDB", result);
        assertEquals(Duration.ofMinutes(1), ((SimpleLocalCache) l1).ttlOf("user:1"));
    }

    @Test
    void get_miss_nullFromL3_cachesNullValue() {
        when(valueOps.get("user:1")).thenReturn(null);

        String result = template.get("user", "1", String.class, Duration.ofMinutes(5), k -> null);

        assertNull(result);
        verify(valueOps).set(eq("user:1"), eq("__REDIS_PLUS_NULL__"), eq(Duration.ofMinutes(1)));
    }

    @Test
    void get_miss_acquiresLoadLockWithRuntimePolicyDurations() {
        CacheLoadProtection loadProtection = mock(CacheLoadProtection.class);
        when(loadProtection.acquire(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(CacheLoadProtection.LockHandle.noop("lock"));
        CacheRuntimePolicy policy = new CacheRuntimePolicy(
                Duration.ofSeconds(4), Duration.ofSeconds(9), 1_000, 1_000, 0.2,
                Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(5));
        ThreeLevelCacheTemplate customTemplate = new ThreeLevelCacheTemplate(
                l1, redisTemplate, serializer, loadProtection, ExpireStrategy.fixed(), CacheMetricsCollector.noop(),
                "", io.github.guanxiangkai.redis.plus.cache.spi.CacheConsistencyStrategy.invalidate(),
                new io.github.guanxiangkai.redis.plus.core.key.DefaultKeyNamingStrategy(),
                io.github.guanxiangkai.redis.plus.core.observation.RedisPlusObserver.noop(), policy);
        when(valueOps.get("user:1")).thenReturn(null);

        customTemplate.get("user", "1", String.class, Duration.ofMinutes(5), key -> "Alice");

        verify(loadProtection).acquire(eq("redis-plus:cache:load:user:1"), eq(4_000L), eq(9_000L),
                eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void put_writesL1AndL2() {
        template.put("user", "1", "Alice", Duration.ofMinutes(5));

        verify(valueOps).set(eq("user:1"), eq("Alice"), eq(Duration.ofMinutes(5)));
        assertEquals("Alice", l1.get("user:1"));
    }

    @Test
    void evict_removesFromL1AndL2() {
        l1.put("user:1", "Alice", Duration.ofMinutes(5));
        template.evict("user", "1");

        assertNull(l1.get("user:1"));
        verify(redisTemplate).delete("user:1");
    }

    @Test
    void buildCacheKey_noPrefix() {
        assertEquals("user:42", template.buildCacheKey("user", "42"));
    }

    @Test
    void buildCacheKey_withPrefix() {
        ThreeLevelCacheTemplate t = new ThreeLevelCacheTemplate(
                l1, redisTemplate, serializer,
                CacheLoadProtection.noProtection(),
                ExpireStrategy.fixed(),
                CacheMetricsCollector.noop(),
                "app:",
                io.github.guanxiangkai.redis.plus.cache.spi.CacheConsistencyStrategy.invalidate(),
                new io.github.guanxiangkai.redis.plus.core.key.DefaultKeyNamingStrategy(),
                io.github.guanxiangkai.redis.plus.core.observation.RedisPlusObserver.noop(),
                CacheRuntimePolicy.defaults()
        );
        assertEquals("app:user:42", t.buildCacheKey("user", "42"));
    }

    @Test
    void clear_deletesRedisKeysInBatches() {
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.Cursor<String> cursor =
                mock(org.springframework.data.redis.core.Cursor.class);
        when(redisTemplate.scan(any())).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn("user:1", "user:2");

        template.clear("user");

        verify(redisTemplate).delete(argThat((Collection<String> keys) ->
                keys.size() == 2 && keys.contains("user:1") && keys.contains("user:2")));
        verify(cursor).close();
    }

    @Test
    void clear_usesConfiguredDeleteBatchSize() {
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.Cursor<String> cursor =
                mock(org.springframework.data.redis.core.Cursor.class);
        when(redisTemplate.scan(any())).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn("user:1", "user:2");
        CacheRuntimePolicy policy = new CacheRuntimePolicy(
                Duration.ofSeconds(10), Duration.ofSeconds(30), 10, 1, 0.2,
                Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(5));
        ThreeLevelCacheTemplate customTemplate = new ThreeLevelCacheTemplate(
                l1, redisTemplate, serializer, CacheLoadProtection.noProtection(), ExpireStrategy.fixed(),
                CacheMetricsCollector.noop(), "", io.github.guanxiangkai.redis.plus.cache.spi.CacheConsistencyStrategy.invalidate(),
                new io.github.guanxiangkai.redis.plus.core.key.DefaultKeyNamingStrategy(),
                io.github.guanxiangkai.redis.plus.core.observation.RedisPlusObserver.noop(), policy);

        customTemplate.clear("user");

        verify(redisTemplate, times(2)).delete(argThat((Collection<String> keys) -> keys.size() == 1));
    }

    // Simple in-memory L1 cache for testing
    static class SimpleLocalCache implements LocalCacheProvider {
        private final ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Duration> ttlMap = new ConcurrentHashMap<>();

        @Override
        public Object get(String key) {
            return map.get(key);
        }

        @Override
        public void put(String key, Object value, Duration ttl) {
            map.put(key, value);
            ttlMap.put(key, ttl);
        }

        @Override
        public void evict(String key) {
            map.remove(key);
            ttlMap.remove(key);
        }

        @Override
        public void clear() {
            map.clear();
            ttlMap.clear();
        }

        @Override
        public void clearByPrefix(String keyPrefix) {
            map.keySet().removeIf(k -> k.startsWith(keyPrefix));
            ttlMap.keySet().removeIf(k -> k.startsWith(keyPrefix));
        }

        Duration ttlOf(String key) {
            return ttlMap.get(key);
        }
    }
}
