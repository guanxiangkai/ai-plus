package io.github.guanxiangkai.redis.plus.ratelimit.impl;

import io.github.guanxiangkai.redis.plus.core.key.DefaultKeyNamingStrategy;
import io.github.guanxiangkai.redis.plus.ratelimit.RateLimitConfig;
import org.junit.jupiter.api.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;
import org.redisson.api.ratelimiter.RateLimiterArgs;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedissonTokenBucketRateLimiterTest {

    @Test
    void tryAcquire_configuresLimiterWithRedissonFourArgumentsApi() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RRateLimiter rateLimiter = mock(RRateLimiter.class);
        when(redissonClient.getRateLimiter("test:api:orders")).thenReturn(rateLimiter);
        when(rateLimiter.getConfig()).thenReturn(null);
        when(rateLimiter.tryAcquire()).thenReturn(true);
        RedissonTokenBucketRateLimiter limiter =
                new RedissonTokenBucketRateLimiter(redissonClient, "test:", new DefaultKeyNamingStrategy());

        boolean acquired = limiter.tryAcquire("api:orders",
                new RateLimitConfig.TokenBucket(10, 5, Duration.ofSeconds(1)));

        assertThat(acquired).isTrue();
        verify(rateLimiter).setRate(any(RateLimiterArgs.class));
    }

    @Test
    void tryAcquire_reusesExistingRateWhenConfigMatches() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RRateLimiter rateLimiter = mock(RRateLimiter.class);
        org.redisson.api.RateLimiterConfig config = mock(org.redisson.api.RateLimiterConfig.class);
        when(redissonClient.getRateLimiter("test:api:orders")).thenReturn(rateLimiter);
        when(config.getRate()).thenReturn(10L);
        when(config.getRateInterval()).thenReturn(200L);
        when(rateLimiter.getConfig()).thenReturn(config);
        when(rateLimiter.tryAcquire()).thenReturn(true);
        RedissonTokenBucketRateLimiter limiter =
                new RedissonTokenBucketRateLimiter(redissonClient, "test:", new DefaultKeyNamingStrategy());

        boolean acquired = limiter.tryAcquire("api:orders",
                new RateLimitConfig.TokenBucket(10, 5, Duration.ofMillis(100)));

        assertThat(acquired).isTrue();
        verify(rateLimiter, never()).setRate(any(RateLimiterArgs.class));
    }
}
