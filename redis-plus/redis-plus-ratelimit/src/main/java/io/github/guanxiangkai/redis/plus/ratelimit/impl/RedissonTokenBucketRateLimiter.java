package io.github.guanxiangkai.redis.plus.ratelimit.impl;

import io.github.guanxiangkai.redis.plus.core.key.KeyNamespaceUtils;
import io.github.guanxiangkai.redis.plus.core.key.KeyNamingStrategy;
import io.github.guanxiangkai.redis.plus.ratelimit.RateLimitConfig;
import io.github.guanxiangkai.redis.plus.ratelimit.RateLimiter;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateLimiterConfig;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.api.ratelimiter.RateLimiterArgs;

import java.time.Duration;

/**
 * 基于 Redisson 的分布式令牌桶限流器。
 */
public class RedissonTokenBucketRateLimiter implements RateLimiter<RateLimitConfig.TokenBucket> {

    private final RedissonClient redissonClient;
    private final String keyNamespace;
    private final KeyNamingStrategy keyNamingStrategy;

    public RedissonTokenBucketRateLimiter(RedissonClient redissonClient,
                                          String keyPrefix,
                                          KeyNamingStrategy keyNamingStrategy) {
        this.redissonClient = redissonClient;
        this.keyNamespace = KeyNamespaceUtils.namespace(keyPrefix, "redis-plus:ratelimit:token");
        this.keyNamingStrategy = keyNamingStrategy;
    }

    @Override
    public boolean tryAcquire(String key, RateLimitConfig.TokenBucket config) {
        String rateLimiterKey = keyNamingStrategy.resolve(keyNamespace, key);
        RRateLimiter limiter = redissonClient.getRateLimiter(rateLimiterKey);
        configure(limiter, config);
        return limiter.tryAcquire();
    }

    private void configure(RRateLimiter limiter, RateLimitConfig.TokenBucket config) {
        long capacity = config.capacity();
        Duration interval = intervalFor(config);
        RateLimiterConfig current = limiter.getConfig();
        if (current == null || current.getRate() == null || current.getRateInterval() == null
                || current.getRate() != capacity || current.getRateInterval() != interval.toMillis()) {
            limiter.setRate(RateLimiterArgs.of(RateType.OVERALL, capacity, interval).keepState(true));
        }
    }

    private static Duration intervalFor(RateLimitConfig.TokenBucket config) {
        long nanos = Math.max(1L,
                Math.multiplyExact(config.refillPeriod().toNanos(), config.capacity()) / config.refillTokens());
        return Duration.ofNanos(nanos);
    }
}
