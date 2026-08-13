package io.github.guanxiangkai.redis.plus.autoconfigure.cache;

import io.github.guanxiangkai.redis.plus.autoconfigure.lock.RedisPlusLockAutoConfiguration;
import io.github.guanxiangkai.redis.plus.autoconfigure.properties.RedisPlusCacheProperties;
import io.github.guanxiangkai.redis.plus.cache.CacheRuntimePolicy;
import io.github.guanxiangkai.redis.plus.cache.ThreeLevelCacheTemplate;
import io.github.guanxiangkai.redis.plus.cache.aop.ThreeLevelCacheAspect;
import io.github.guanxiangkai.redis.plus.cache.impl.CacheMetricsCollectorAdapter;
import io.github.guanxiangkai.redis.plus.cache.local.CaffeineLocalCacheProvider;
import io.github.guanxiangkai.redis.plus.cache.protection.DistributedCacheLoadProtection;
import io.github.guanxiangkai.redis.plus.cache.serializer.JacksonValueSerializer;
import io.github.guanxiangkai.redis.plus.cache.spring.RedisPlusCacheManager;
import io.github.guanxiangkai.redis.plus.cache.spi.*;
import io.github.guanxiangkai.redis.plus.core.expire.ExpireStrategy;
import io.github.guanxiangkai.redis.plus.core.key.KeyNamingStrategy;
import io.github.guanxiangkai.redis.plus.core.metrics.RedisPlusMetrics;
import io.github.guanxiangkai.redis.plus.core.observation.RedisPlusObserver;
import io.github.guanxiangkai.redis.plus.core.serializer.ValueSerializer;
import io.github.guanxiangkai.redis.plus.lock.impl.RedisLockFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * 三级缓存自动装配
 *
 * <p>注册的 Bean：
 * <ul>
 *   <li>{@link LocalCacheProvider} — L1 本地缓存（默认 Caffeine）</li>
 *   <li>{@link ValueSerializer} — 缓存值序列化器（Jackson）</li>
 *   <li>{@link ExpireStrategy} — TTL 策略（随机抖动防雪崩）</li>
 *   <li>{@link CacheLoadProtection} — 回源保护（有 lock 模块时用分布式锁，否则本地锁）</li>
 *   <li>{@link CacheConsistencyStrategy} — 缓存一致性策略（默认直接失效）</li>
 *   <li>{@link ThreeLevelCacheTemplate} — 三级缓存编程式入口</li>
 *   <li>{@link ThreeLevelCacheAspect} — AOP 注解驱动</li>
 * </ul>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@AutoConfiguration(after = RedisPlusLockAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "redis-plus.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedisPlusCacheProperties.class)
@SuppressWarnings("NullAway")
public class RedisPlusCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LocalCacheProvider.class)
    public LocalCacheProvider caffeineLocalCacheProvider(RedisPlusCacheProperties properties) {
        var localProps = properties.getLocal();
        return new CaffeineLocalCacheProvider(localProps.getMaximumSize(), localProps.getTtl());
    }

    @Bean
    @ConditionalOnMissingBean(ValueSerializer.class)
    public ValueSerializer valueSerializer(ObjectProvider<ObjectMapper> objectMapperProvider,
                                                     RedisPlusCacheProperties properties) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new JacksonValueSerializer(objectMapper, properties.getAllowedPackages());
    }

    @Bean
    @ConditionalOnMissingBean(ExpireStrategy.class)
    public ExpireStrategy expireStrategy(RedisPlusCacheProperties properties) {
        double jitter = properties.getJitterRatio();
        return jitter > 0 ? ExpireStrategy.randomJitter(jitter) : ExpireStrategy.fixed();
    }

    @Bean
    @ConditionalOnMissingBean(CacheMetricsCollector.class)
    public CacheMetricsCollector cacheMetricsCollector(ObjectProvider<RedisPlusMetrics> metricsProvider) {
        return new CacheMetricsCollectorAdapter(metricsProvider);
    }

    /**
     * 缓存一致性策略（默认直接失效）。
     * 用户可通过注册自定义 {@link CacheConsistencyStrategy} Bean 替换为延迟双删或写穿策略。
     */
    @Bean
    @ConditionalOnMissingBean(CacheConsistencyStrategy.class)
    public CacheConsistencyStrategy cacheConsistencyStrategy() {
        return CacheConsistencyStrategy.invalidate();
    }

    /**
     * 回源保护：有 {@link RedisLockFactory} 时使用分布式锁，否则回退到 JVM 本地锁。
     */
    @Bean
    @ConditionalOnMissingBean(CacheLoadProtection.class)
    @ConditionalOnClass(name = "io.github.guanxiangkai.redis.plus.lock.impl.RedisLockFactory")
    public CacheLoadProtection distributedCacheLoadProtection(ObjectProvider<RedisLockFactory> lockFactoryProvider) {
        RedisLockFactory lockFactory = lockFactoryProvider.getIfAvailable();
        if (lockFactory != null) {
            return new DistributedCacheLoadProtection(lockFactory);
        }
        return CacheLoadProtection.local();
    }

    @Bean
    @ConditionalOnMissingBean(CacheLoadProtection.class)
    public CacheLoadProtection localCacheLoadProtection() {
        return CacheLoadProtection.local();
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheRuntimePolicy cacheRuntimePolicy(RedisPlusCacheProperties properties) {
        return properties.getRuntime().toPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public ThreeLevelCacheTemplate threeLevelCacheTemplate(LocalCacheProvider l1,
                                                           StringRedisTemplate redisTemplate,
                                                           ValueSerializer serializer,
                                                           CacheLoadProtection loadProtection,
                                                           ExpireStrategy expireStrategy,
                                                           CacheMetricsCollector cacheMetricsCollector,
                                                           CacheConsistencyStrategy consistencyStrategy,
                                                           KeyNamingStrategy keyNamingStrategy,
                                                           RedisPlusObserver observer,
                                                           CacheRuntimePolicy runtimePolicy,
                                                           RedisPlusCacheProperties properties) {
        String keyPrefix = properties.getKeyPrefix();
        return new ThreeLevelCacheTemplate(l1, redisTemplate, serializer, loadProtection,
                expireStrategy, cacheMetricsCollector, keyPrefix, consistencyStrategy, keyNamingStrategy, observer,
                runtimePolicy);
    }

    @Bean
    @ConditionalOnMissingBean
    public ThreeLevelCacheAspect threeLevelCacheAspect(ThreeLevelCacheTemplate cacheTemplate,
                                                       ObjectProvider<CacheKeyResolver> keyResolverProvider) {
        return new ThreeLevelCacheAspect(cacheTemplate, keyResolverProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager redisPlusCacheManager(ThreeLevelCacheTemplate cacheTemplate,
                                              RedisPlusCacheProperties properties) {
        return new RedisPlusCacheManager(cacheTemplate, properties.getDefaultTtl());
    }
}
