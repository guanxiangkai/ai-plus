package io.github.guanxiangkai.redis.plus.ratelimit.spi;

import io.github.guanxiangkai.redis.plus.ratelimit.RateLimitConfig;
import io.github.guanxiangkai.redis.plus.ratelimit.RateLimiter;

/**
 * Factory methods for rate-limit algorithm adapters.
 */
public final class RateLimitAlgorithms {

    private RateLimitAlgorithms() {
    }

    public static <C extends RateLimitConfig> RateLimitAlgorithm named(
            String name,
            Class<C> configType,
            RateLimiter<C> limiter) {
        return new RateLimitAlgorithm() {
            @Override
            public String algorithmName() {
                return name;
            }

            @Override
            public boolean tryAcquire(String key, RateLimitConfig config) {
                return limiter.tryAcquire(key, configType.cast(config));
            }
        };
    }
}
