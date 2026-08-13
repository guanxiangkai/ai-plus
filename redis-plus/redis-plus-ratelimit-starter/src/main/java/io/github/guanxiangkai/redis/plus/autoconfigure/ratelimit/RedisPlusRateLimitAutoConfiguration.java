package io.github.guanxiangkai.redis.plus.autoconfigure.ratelimit;

import io.github.guanxiangkai.redis.plus.autoconfigure.cache.RedisPlusCacheAutoConfiguration;
import io.github.guanxiangkai.redis.plus.autoconfigure.governance.RedisPlusGovernanceAutoConfiguration;
import io.github.guanxiangkai.redis.plus.autoconfigure.properties.RedisPlusRateLimitProperties;
import io.github.guanxiangkai.redis.plus.core.invoke.InvocationContexts;
import io.github.guanxiangkai.redis.plus.core.key.KeyNamingStrategy;
import io.github.guanxiangkai.redis.plus.core.observation.RedisPlusObserver;
import io.github.guanxiangkai.redis.plus.core.script.RedisScriptExecutor;
import io.github.guanxiangkai.redis.plus.ratelimit.RateLimitConfig;
import io.github.guanxiangkai.redis.plus.ratelimit.RateLimiter;
import io.github.guanxiangkai.redis.plus.ratelimit.aop.RateLimitAspect;
import io.github.guanxiangkai.redis.plus.ratelimit.impl.Bucket4jTokenBucketRateLimiter;
import io.github.guanxiangkai.redis.plus.ratelimit.impl.FixedWindowRateLimiter;
import io.github.guanxiangkai.redis.plus.ratelimit.impl.LeakyBucketRateLimiter;
import io.github.guanxiangkai.redis.plus.ratelimit.impl.RedissonTokenBucketRateLimiter;
import io.github.guanxiangkai.redis.plus.ratelimit.impl.SlidingWindowRateLimiter;
import io.github.guanxiangkai.redis.plus.ratelimit.spi.RateLimitAlgorithm;
import io.github.guanxiangkai.redis.plus.ratelimit.spi.RateLimitAlgorithmRegistry;
import io.github.guanxiangkai.redis.plus.ratelimit.spi.RateLimitAlgorithms;
import io.github.guanxiangkai.redis.plus.ratelimit.spi.RateLimitKeyResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.redisson.api.RedissonClient;

/**
 * Redis 限流能力自动装配。
 */
@AutoConfiguration(after = {RedisPlusCacheAutoConfiguration.class, RedisPlusGovernanceAutoConfiguration.class})
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "redis-plus.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedisPlusRateLimitProperties.class)
public class RedisPlusRateLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SlidingWindowRateLimiter slidingWindowRateLimiter(RedisScriptExecutor scriptExecutor,
                                                             KeyNamingStrategy keyNamingStrategy,
                                                             RedisPlusRateLimitProperties properties) {
        String prefix = properties.getKeyPrefix() + "sliding:";
        return new SlidingWindowRateLimiter(scriptExecutor, prefix, keyNamingStrategy);
    }

    @Bean
    @ConditionalOnMissingBean
    public FixedWindowRateLimiter fixedWindowRateLimiter(RedisScriptExecutor scriptExecutor,
                                                         KeyNamingStrategy keyNamingStrategy,
                                                         RedisPlusRateLimitProperties properties) {
        String prefix = properties.getKeyPrefix() + "fixed:";
        return new FixedWindowRateLimiter(scriptExecutor, prefix, keyNamingStrategy);
    }

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean(name = "tokenBucketRateLimiter")
    public RedissonTokenBucketRateLimiter tokenBucketRateLimiter(RedissonClient redissonClient,
                                                                 KeyNamingStrategy keyNamingStrategy,
                                                                 RedisPlusRateLimitProperties properties) {
        String prefix = properties.getKeyPrefix() + "token:";
        return new RedissonTokenBucketRateLimiter(redissonClient, prefix, keyNamingStrategy);
    }

    @Bean
    @ConditionalOnMissingBean(name = "tokenBucketRateLimiter")
    public Bucket4jTokenBucketRateLimiter localTokenBucketRateLimiter(KeyNamingStrategy keyNamingStrategy,
                                                                      RedisPlusRateLimitProperties properties) {
        String prefix = properties.getKeyPrefix() + "token:";
        return new Bucket4jTokenBucketRateLimiter(prefix, keyNamingStrategy);
    }

    @Bean
    @ConditionalOnMissingBean
    public LeakyBucketRateLimiter leakyBucketRateLimiter(RedisScriptExecutor scriptExecutor,
                                                         KeyNamingStrategy keyNamingStrategy,
                                                         RedisPlusRateLimitProperties properties) {
        String prefix = properties.getKeyPrefix() + "leaky:";
        return new LeakyBucketRateLimiter(scriptExecutor, prefix, keyNamingStrategy);
    }

    @Bean
    @ConditionalOnMissingBean(name = "fixedWindowRateLimitAlgorithm")
    public RateLimitAlgorithm fixedWindowRateLimitAlgorithm(FixedWindowRateLimiter fixedWindow) {
        return RateLimitAlgorithms.named("FIXED_WINDOW", RateLimitConfig.FixedWindow.class, fixedWindow);
    }

    @Bean
    @ConditionalOnMissingBean(name = "slidingWindowRateLimitAlgorithm")
    public RateLimitAlgorithm slidingWindowRateLimitAlgorithm(SlidingWindowRateLimiter slidingWindow) {
        return RateLimitAlgorithms.named("SLIDING_WINDOW", RateLimitConfig.SlidingWindow.class, slidingWindow);
    }

    @Bean
    @ConditionalOnMissingBean(name = "tokenBucketRateLimitAlgorithm")
    public RateLimitAlgorithm tokenBucketRateLimitAlgorithm(RateLimiter<RateLimitConfig.TokenBucket> tokenBucketRateLimiter) {
        return RateLimitAlgorithms.named("TOKEN_BUCKET", RateLimitConfig.TokenBucket.class, tokenBucketRateLimiter);
    }

    @Bean
    @ConditionalOnMissingBean(name = "leakyBucketRateLimitAlgorithm")
    public RateLimitAlgorithm leakyBucketRateLimitAlgorithm(LeakyBucketRateLimiter leakyBucket) {
        return RateLimitAlgorithms.named("LEAKY_BUCKET", RateLimitConfig.LeakyBucket.class, leakyBucket);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitKeyResolver rateLimitKeyResolver() {
        return InvocationContexts::resolveKey;
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAlgorithmRegistry rateLimitAlgorithmRegistry(ObjectProvider<RateLimitAlgorithm> algorithms) {
        return new RateLimitAlgorithmRegistry(algorithms.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(RateLimitAlgorithmRegistry algorithmRegistry,
                                           ObjectProvider<RateLimitKeyResolver> keyResolverProvider,
                                           RedisPlusObserver observer,
                                           RedisPlusRateLimitProperties properties) {
        return new RateLimitAspect(algorithmRegistry, keyResolverProvider.getObject(),
                properties.getTokenBucketRefillRate(), observer);
    }
}
