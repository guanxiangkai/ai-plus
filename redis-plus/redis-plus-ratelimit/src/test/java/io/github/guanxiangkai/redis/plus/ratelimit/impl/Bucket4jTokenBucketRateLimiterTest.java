package io.github.guanxiangkai.redis.plus.ratelimit.impl;

import io.github.guanxiangkai.redis.plus.core.key.DefaultKeyNamingStrategy;
import io.github.guanxiangkai.redis.plus.ratelimit.RateLimitConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bucket4jTokenBucketRateLimiterTest {

    @Test
    void tryAcquire_rejectsWhenBucketIsEmpty() {
        Bucket4jTokenBucketRateLimiter limiter =
                new Bucket4jTokenBucketRateLimiter("test:", new DefaultKeyNamingStrategy());
        RateLimitConfig.TokenBucket config =
                new RateLimitConfig.TokenBucket(2, 1, Duration.ofSeconds(10));

        assertTrue(limiter.tryAcquire("api:orders", config));
        assertTrue(limiter.tryAcquire("api:orders", config));
        assertFalse(limiter.tryAcquire("api:orders", config));
    }

    @Test
    void tryAcquire_usesIndependentBucketsPerKey() {
        Bucket4jTokenBucketRateLimiter limiter =
                new Bucket4jTokenBucketRateLimiter("test:", new DefaultKeyNamingStrategy());
        RateLimitConfig.TokenBucket config =
                new RateLimitConfig.TokenBucket(1, 1, Duration.ofSeconds(10));

        assertTrue(limiter.tryAcquire("api:a", config));
        assertFalse(limiter.tryAcquire("api:a", config));
        assertTrue(limiter.tryAcquire("api:b", config));
    }
}
