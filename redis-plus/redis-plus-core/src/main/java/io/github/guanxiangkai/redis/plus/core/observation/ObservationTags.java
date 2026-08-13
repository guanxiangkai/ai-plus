package io.github.guanxiangkai.redis.plus.core.observation;

import java.util.Map;

/**
 * Factory methods for common Redis Plus observation tags.
 */
public final class ObservationTags {

    private ObservationTags() {
    }

    public static Map<String, String> none() {
        return Map.of();
    }

    public static Map<String, String> cacheName(String cacheName) {
        return Map.of("cache.name", cacheName);
    }

    public static Map<String, String> cacheKey(String key) {
        return Map.of("cache.key", key);
    }

    public static Map<String, String> lockKey(String key) {
        return Map.of("lock.key", key);
    }

    public static Map<String, String> rateLimitAlgorithm(String algorithm) {
        return Map.of("algorithm", algorithm);
    }

    public static Map<String, String> rateLimitKey(String key) {
        return Map.of("ratelimit.key", key);
    }

    public static Map<String, String> idempotentKey(String key) {
        return Map.of("idempotent.key", key);
    }

    public static Map<String, String> queueType(String type) {
        return Map.of("queue.type", type);
    }

    public static Map<String, String> queueName(String name) {
        return Map.of("queue.name", name);
    }
}
