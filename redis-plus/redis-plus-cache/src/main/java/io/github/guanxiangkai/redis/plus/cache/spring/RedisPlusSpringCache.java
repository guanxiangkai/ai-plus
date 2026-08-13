package io.github.guanxiangkai.redis.plus.cache.spring;

import io.github.guanxiangkai.redis.plus.cache.ThreeLevelCacheTemplate;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Spring Cache adapter backed by {@link ThreeLevelCacheTemplate}.
 */
@SuppressWarnings({"unchecked", "NullAway"})
public class RedisPlusSpringCache implements Cache {

    private final String name;
    private final ThreeLevelCacheTemplate cacheTemplate;
    private final Duration ttl;

    public RedisPlusSpringCache(String name, ThreeLevelCacheTemplate cacheTemplate, Duration ttl) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.cacheTemplate = Objects.requireNonNull(cacheTemplate, "cacheTemplate must not be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return cacheTemplate;
    }

    @Override
    public ValueWrapper get(Object key) {
        Object value = cacheTemplate.get(name, keyToString(key), Object.class, ttl, ignored -> null);
        return value != null ? new SimpleValueWrapper(value) : null;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        return cacheTemplate.get(name, keyToString(key), type, ttl, ignored -> null);
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object value = cacheTemplate.get(name, keyToString(key), Object.class, ttl, ignored -> {
            try {
                return valueLoader.call();
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
        });
        return (T) value;
    }

    @Override
    public void put(Object key, Object value) {
        if (value == null) {
            evict(key);
            return;
        }
        cacheTemplate.put(name, keyToString(key), value, ttl);
    }

    @Override
    public void evict(Object key) {
        cacheTemplate.evict(name, keyToString(key));
    }

    @Override
    public void clear() {
        cacheTemplate.clear(name);
    }

    private static String keyToString(Object key) {
        return Objects.requireNonNull(key, "key must not be null").toString();
    }
}
