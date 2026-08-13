package io.github.guanxiangkai.redis.plus.cache.spring;

import io.github.guanxiangkai.redis.plus.cache.ThreeLevelCacheTemplate;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring {@link CacheManager} backed by Redis Plus three-level caches.
 */
public class RedisPlusCacheManager implements CacheManager {

    private final ThreeLevelCacheTemplate cacheTemplate;
    private final Duration defaultTtl;
    private final ConcurrentHashMap<String, Cache> caches = new ConcurrentHashMap<>();

    public RedisPlusCacheManager(ThreeLevelCacheTemplate cacheTemplate, Duration defaultTtl) {
        this.cacheTemplate = cacheTemplate;
        this.defaultTtl = defaultTtl;
    }

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, key -> new RedisPlusSpringCache(key, cacheTemplate, defaultTtl));
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(caches.keySet());
    }
}
