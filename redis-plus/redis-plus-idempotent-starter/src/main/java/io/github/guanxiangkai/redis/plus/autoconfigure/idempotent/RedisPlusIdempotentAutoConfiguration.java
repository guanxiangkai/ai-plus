package io.github.guanxiangkai.redis.plus.autoconfigure.idempotent;

import io.github.guanxiangkai.redis.plus.autoconfigure.cache.RedisPlusCacheAutoConfiguration;
import io.github.guanxiangkai.redis.plus.autoconfigure.governance.RedisPlusGovernanceAutoConfiguration;
import io.github.guanxiangkai.redis.plus.autoconfigure.properties.RedisPlusIdempotentProperties;
import io.github.guanxiangkai.redis.plus.core.key.KeyNamingStrategy;
import io.github.guanxiangkai.redis.plus.core.observation.RedisPlusObserver;
import io.github.guanxiangkai.redis.plus.core.script.RedisScriptExecutor;
import io.github.guanxiangkai.redis.plus.core.serializer.ValueSerializer;
import io.github.guanxiangkai.redis.plus.idempotent.IdempotentExecutor;
import io.github.guanxiangkai.redis.plus.idempotent.aop.IdempotentAspect;
import io.github.guanxiangkai.redis.plus.idempotent.impl.RedisIdempotentExecutor;
import io.github.guanxiangkai.redis.plus.idempotent.impl.RedisIdempotentStateStore;
import io.github.guanxiangkai.redis.plus.idempotent.spi.IdempotentKeyResolver;
import io.github.guanxiangkai.redis.plus.idempotent.spi.IdempotentStateStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 幂等能力自动装配。
 */
@AutoConfiguration(after = {RedisPlusCacheAutoConfiguration.class, RedisPlusGovernanceAutoConfiguration.class})
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "redis-plus.idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedisPlusIdempotentProperties.class)
@SuppressWarnings("NullAway")
public class RedisPlusIdempotentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotentStateStore.class)
    public RedisIdempotentStateStore redisIdempotentStateStore(StringRedisTemplate redisTemplate,
                                                               RedisScriptExecutor scriptExecutor,
                                                               ValueSerializer valueSerializer) {
        return new RedisIdempotentStateStore(redisTemplate, scriptExecutor, valueSerializer);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentExecutor.class)
    public RedisIdempotentExecutor redisIdempotentExecutor(IdempotentStateStore stateStore,
                                                           ValueSerializer valueSerializer,
                                                           KeyNamingStrategy keyNamingStrategy,
                                                           RedisPlusObserver observer,
                                                           RedisPlusIdempotentProperties properties) {
        String keyPrefix = properties.getKeyPrefix();
        return new RedisIdempotentExecutor(stateStore, valueSerializer,
                keyPrefix, keyNamingStrategy, observer);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect idempotentAspect(IdempotentExecutor executor,
                                             ObjectProvider<IdempotentKeyResolver> keyResolverProvider) {
        return new IdempotentAspect(executor, keyResolverProvider.getIfAvailable());
    }
}
