package io.github.guanxiangkai.redis.plus.autoconfigure.enhance;

import io.github.guanxiangkai.redis.plus.autoconfigure.cache.RedisPlusCacheAutoConfiguration;
import io.github.guanxiangkai.redis.plus.autoconfigure.properties.RedisPlusEnhanceProperties;
import io.github.guanxiangkai.redis.plus.cache.ThreeLevelCacheTemplate;
import io.github.guanxiangkai.redis.plus.cache.CacheRuntimePolicy;
import io.github.guanxiangkai.redis.plus.core.serializer.ValueSerializer;
import io.github.guanxiangkai.redis.plus.cache.spi.LocalCacheProvider;
import io.github.guanxiangkai.redis.plus.core.script.RedisScriptExecutor;
import io.github.guanxiangkai.redis.plus.enhance.batch.BatchCacheOperations;
import io.github.guanxiangkai.redis.plus.enhance.batch.impl.RedisBatchCacheOperations;
import io.github.guanxiangkai.redis.plus.enhance.bloom.BloomFilter;
import io.github.guanxiangkai.redis.plus.enhance.bloom.aop.BloomCheckAspect;
import io.github.guanxiangkai.redis.plus.enhance.bloom.impl.RedisBitmapBloomFilter;
import io.github.guanxiangkai.redis.plus.enhance.bloom.spi.BloomHashProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

/**
 * 缓存增强能力自动装配（布隆过滤器 + 批量操作）。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@AutoConfiguration(after = RedisPlusCacheAutoConfiguration.class)
@ConditionalOnProperty(prefix = "redis-plus.enhance", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedisPlusEnhanceProperties.class)
public class RedisPlusEnhanceAutoConfiguration {

    // ── 布隆过滤器 ──────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(BloomHashProvider.class)
    public BloomHashProvider bloomHashProvider() {
        return BloomHashProvider.fnv1a();
    }

    /**
     * 默认布隆过滤器（名称为 "default"）。
     * 如需多个过滤器，用户可自行注册 {@code Map<String, BloomFilter>} Bean。
     */
    @Bean("defaultBloomFilter")
    @ConditionalOnMissingBean(name = "defaultBloomFilter")
    @ConditionalOnProperty(prefix = "redis-plus.enhance.bloom", name = "enabled", matchIfMissing = true)
    public BloomFilter<String> defaultBloomFilter(BloomHashProvider hashProvider,
                                                  StringRedisTemplate redisTemplate,
                                                  RedisScriptExecutor scriptExecutor,
                                                  RedisPlusEnhanceProperties properties) {
        var bloomProps = properties.getBloom();
        return new RedisBitmapBloomFilter<>(
                "default",
                bloomProps.getExpectedInsertions(),
                bloomProps.getFalsePositiveProbability(),
                bloomProps.getVersion(),
                hashProvider,
                redisTemplate,
                scriptExecutor);
    }

    @Bean
    @ConditionalOnMissingBean(BloomCheckAspect.class)
    @ConditionalOnProperty(prefix = "redis-plus.enhance.bloom", name = "enabled", matchIfMissing = true)
    public BloomCheckAspect bloomCheckAspect(Map<String, BloomFilter<String>> bloomFilters) {
        return new BloomCheckAspect(bloomFilters);
    }

    // ── 批量操作 ─────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(BatchCacheOperations.class)
    public BatchCacheOperations batchCacheOperations(ThreeLevelCacheTemplate cacheTemplate,
                                                     LocalCacheProvider l1,
                                                     StringRedisTemplate redisTemplate,
                                                     ValueSerializer serializer,
                                                     CacheRuntimePolicy runtimePolicy) {
        return new RedisBatchCacheOperations(cacheTemplate, l1, redisTemplate, serializer, runtimePolicy);
    }
}
