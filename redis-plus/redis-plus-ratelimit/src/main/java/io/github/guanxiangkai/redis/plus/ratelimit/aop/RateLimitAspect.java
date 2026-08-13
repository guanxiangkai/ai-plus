package io.github.guanxiangkai.redis.plus.ratelimit.aop;

import io.github.guanxiangkai.redis.plus.core.aop.RedisPlusAspectOrder;
import io.github.guanxiangkai.redis.plus.core.aop.RedisPlusJoinPoints;
import io.github.guanxiangkai.redis.plus.core.exception.RedisPlusException;
import io.github.guanxiangkai.redis.plus.core.invoke.InvocationContext;
import io.github.guanxiangkai.redis.plus.core.invoke.InvocationContexts;
import io.github.guanxiangkai.redis.plus.core.observation.RedisPlusObservationType;
import io.github.guanxiangkai.redis.plus.core.observation.RedisPlusObserver;
import io.github.guanxiangkai.redis.plus.core.observation.ObservationTags;
import io.github.guanxiangkai.redis.plus.ratelimit.RateLimitConfig;
import io.github.guanxiangkai.redis.plus.ratelimit.annotation.RateLimit;
import io.github.guanxiangkai.redis.plus.ratelimit.spi.RateLimitAlgorithmRegistry;
import io.github.guanxiangkai.redis.plus.ratelimit.spi.RateLimitKeyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 限流 AOP 切面
 *
 * <p>拦截 {@link RateLimit} 注解，根据配置算法选择对应的限流算法实现，
 * 并通过 {@link RedisPlusObserver} 接入统一观测。
 */
@Aspect
@Order(RedisPlusAspectOrder.RATELIMIT)
public class RateLimitAspect {

    private final RateLimitAlgorithmRegistry algorithmRegistry;
    private final RateLimitKeyResolver keyResolver;
    private final long defaultTokenBucketRefillTokens;
    private final RedisPlusObserver observer;

    public RateLimitAspect(RateLimitAlgorithmRegistry algorithmRegistry,
                           long defaultTokenBucketRefillTokens) {
        this(algorithmRegistry, InvocationContexts::resolveKey,
                defaultTokenBucketRefillTokens, RedisPlusObserver.noop());
    }

    public RateLimitAspect(RateLimitAlgorithmRegistry algorithmRegistry,
                           RateLimitKeyResolver keyResolver,
                           long defaultTokenBucketRefillTokens,
                           RedisPlusObserver observer) {
        this.algorithmRegistry = Objects.requireNonNull(algorithmRegistry, "algorithmRegistry must not be null");
        this.keyResolver = keyResolver != null ? keyResolver : InvocationContexts::resolveKey;
        this.defaultTokenBucketRefillTokens = defaultTokenBucketRefillTokens;
        this.observer = observer != null ? observer : RedisPlusObserver.noop();
    }

    @Around("@annotation(rateLimit)")
    Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        InvocationContext context = invocationContext(pjp);
        String key = resolveSpel(context, rateLimit.key());
        String algorithmName = rateLimit.algorithm();
        RateLimitConfig config = config(rateLimit);
        return observer.observe(RedisPlusObservationType.RATELIMIT,
                ObservationTags.rateLimitAlgorithm(algorithmName.toLowerCase(Locale.ROOT)),
                ObservationTags.rateLimitKey(key),
                () -> {
                    boolean allowed = algorithmRegistry.getRequired(algorithmName).tryAcquire(key, config);

                    if (!allowed) {
                        throw new RedisPlusException("请求被限流，key=" + key + "，algorithm=" + algorithmName);
                    }
                    return pjp.proceed();
                });
    }

    private RateLimitConfig config(RateLimit rateLimit) {
        return switch (rateLimit.algorithm().trim().toUpperCase(java.util.Locale.ROOT)) {
            case "FIXED_WINDOW" -> fixedWindowConfig(rateLimit);
            case "SLIDING_WINDOW" -> slidingWindowConfig(rateLimit);
            case "TOKEN_BUCKET" -> tokenBucketConfig(rateLimit);
            case "LEAKY_BUCKET" -> leakyBucketConfig(rateLimit);
            default -> slidingWindowConfig(rateLimit);
        };
    }

    private RateLimitConfig.FixedWindow fixedWindowConfig(RateLimit rateLimit) {
        return new RateLimitConfig.FixedWindow(rateLimit.limit(), durationOf(rateLimit.window(), rateLimit.unit()));
    }

    private RateLimitConfig.SlidingWindow slidingWindowConfig(RateLimit rateLimit) {
        return new RateLimitConfig.SlidingWindow(rateLimit.limit(), durationOf(rateLimit.window(), rateLimit.unit()));
    }

    private RateLimitConfig.TokenBucket tokenBucketConfig(RateLimit rateLimit) {
        long capacity = rateLimit.capacity() > 0 ? rateLimit.capacity() : rateLimit.limit();
        long refillTokens = rateLimit.refillTokens() > 0
                ? rateLimit.refillTokens()
                : defaultTokenBucketRefillTokens;
        Duration refillPeriod = durationOf(rateLimit.refillPeriod(), rateLimit.refillUnit());
        return new RateLimitConfig.TokenBucket(capacity, refillTokens, refillPeriod);
    }

    private RateLimitConfig.LeakyBucket leakyBucketConfig(RateLimit rateLimit) {
        long capacity = rateLimit.capacity() > 0 ? rateLimit.capacity() : rateLimit.limit();
        long leakTokens = rateLimit.leakTokens() > 0 ? rateLimit.leakTokens() : rateLimit.limit();
        Duration leakPeriod = rateLimit.leakTokens() > 0
                ? durationOf(rateLimit.leakPeriod(), rateLimit.leakUnit())
                : durationOf(rateLimit.window(), rateLimit.unit());
        return new RateLimitConfig.LeakyBucket(capacity, leakTokens, leakPeriod);
    }

    private Duration durationOf(long value, java.util.concurrent.TimeUnit unit) {
        return Duration.of(value, unit.toChronoUnit());
    }

    private InvocationContext invocationContext(ProceedingJoinPoint pjp) {
        return RedisPlusJoinPoints.invocationContext(pjp);
    }

    private String resolveSpel(InvocationContext context, String expression) {
        return InvocationContexts.resolveKey(context, expression, keyResolver);
    }
}
