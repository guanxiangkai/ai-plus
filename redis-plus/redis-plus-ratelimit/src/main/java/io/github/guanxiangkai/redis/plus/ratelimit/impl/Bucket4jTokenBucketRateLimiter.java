package io.github.guanxiangkai.redis.plus.ratelimit.impl;

import io.github.guanxiangkai.redis.plus.core.key.KeyNamespaceUtils;
import io.github.guanxiangkai.redis.plus.core.key.KeyNamingStrategy;
import io.github.guanxiangkai.redis.plus.ratelimit.RateLimitConfig;
import io.github.guanxiangkai.redis.plus.ratelimit.RateLimiter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Bucket4j 的本地内存令牌桶限流器。
 */
public class Bucket4jTokenBucketRateLimiter implements RateLimiter<RateLimitConfig.TokenBucket> {

    private final ConcurrentHashMap<BucketKey, Bucket> buckets = new ConcurrentHashMap<>();
    private final String keyNamespace;
    private final KeyNamingStrategy keyNamingStrategy;

    public Bucket4jTokenBucketRateLimiter(String keyPrefix, KeyNamingStrategy keyNamingStrategy) {
        this.keyNamespace = KeyNamespaceUtils.namespace(keyPrefix, "redis-plus:ratelimit:token");
        this.keyNamingStrategy = keyNamingStrategy;
    }

    @Override
    public boolean tryAcquire(String key, RateLimitConfig.TokenBucket config) {
        String bucketKey = keyNamingStrategy.resolve(keyNamespace, key);
        Bucket bucket = buckets.computeIfAbsent(BucketKey.of(bucketKey, config), ignored -> newBucket(config));
        return bucket.tryConsume(1);
    }

    private static Bucket newBucket(RateLimitConfig.TokenBucket config) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(config.capacity())
                .refillGreedy(config.refillTokens(), config.refillPeriod())
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private record BucketKey(String key, long capacity, long refillTokens, long refillPeriodNanos) {

        static BucketKey of(String key, RateLimitConfig.TokenBucket config) {
            return new BucketKey(key, config.capacity(), config.refillTokens(), config.refillPeriod().toNanos());
        }
    }
}
