package io.github.guanxiangkai.redis.plus.autoconfigure.lock;

import io.github.guanxiangkai.redis.plus.autoconfigure.core.RedisPlusCoreAutoConfiguration;
import io.github.guanxiangkai.redis.plus.autoconfigure.properties.RedisPlusLockProperties;
import io.github.guanxiangkai.redis.plus.core.key.KeyNamingStrategy;
import io.github.guanxiangkai.redis.plus.core.observation.RedisPlusObserver;
import io.github.guanxiangkai.redis.plus.lock.aop.LockAspect;
import io.github.guanxiangkai.redis.plus.lock.impl.RedissonLockBackend;
import io.github.guanxiangkai.redis.plus.lock.impl.RedisLockFactory;
import io.github.guanxiangkai.redis.plus.lock.impl.SpelLockKeyResolver;
import io.github.guanxiangkai.redis.plus.lock.spi.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 分布式锁自动装配
 *
 * <p>注册 {@link RedisLockFactory}、{@link LockAspect} 以及锁 SPI 默认实现：
 * <ul>
 *   <li>{@link SpelLockKeyResolver} — 默认 SpEL Key 解析器</li>
 * </ul>
 * 用户可通过注册同类型 Bean 替换上述默认实现；
 * {@link LockFailureHandler} 和 {@link LockEventListener} 无内置 Bean，需用户自行注册。
 */
@AutoConfiguration(after = RedisPlusCoreAutoConfiguration.class)
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(prefix = "redis-plus.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedisPlusLockProperties.class)
public class RedisPlusLockAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedissonClient redissonClient(RedisPlusLockProperties properties) {
        return Redisson.create(redissonConfig(properties));
    }

    static Config redissonConfig(RedisPlusLockProperties properties) {
        RedisPlusLockProperties.Redisson redisson = properties.getRedisson();
        Config config = new Config();
        if (StringUtils.hasText(redisson.getPassword())) {
            config.setPassword(redisson.getPassword());
        }
        config.useSingleServer()
                .setAddress(redisson.getAddress())
                .setDatabase(redisson.getDatabase())
                .setConnectTimeout(Math.toIntExact(redisson.getConnectTimeout().toMillis()));
        return config;
    }

    @Bean
    @ConditionalOnMissingBean
    public LockBackend redissonLockBackend(RedissonClient redissonClient) {
        return new RedissonLockBackend(redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisLockFactory redisLockFactory(LockBackend lockBackend,
                                             KeyNamingStrategy keyNamingStrategy,
                                             RedisPlusLockProperties properties) {
        return new RedisLockFactory(properties.getKeyPrefix(), keyNamingStrategy, lockBackend);
    }

    /**
     * 默认 SpEL Lock Key 解析器
     */
    @Bean
    @ConditionalOnMissingBean(LockKeyResolver.class)
    public LockKeyResolver spelLockKeyResolver() {
        return new SpelLockKeyResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public LockAspect lockAspect(RedisLockFactory redisLockFactory,
                                 ObjectProvider<LockKeyResolver> keyResolverProvider,
                                 ObjectProvider<LockFailureHandler> failureHandlerProvider,
                                 RedisPlusObserver observer,
                                 ObjectProvider<List<LockEventListener>> eventListenersProvider,
                                 ApplicationEventPublisher eventPublisher,
                                 RedisPlusLockProperties properties) {
        LockEventPublisher lockEventPublisher = eventPublisher::publishEvent;
        return new LockAspect(redisLockFactory,
                keyResolverProvider.getObject(),
                failureHandlerProvider.getIfAvailable(LockFailureHandler::throwException),
                properties.getDefaultWait(), observer,
                eventListenersProvider.getIfAvailable(List::of), lockEventPublisher);
    }
}
