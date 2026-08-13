package io.github.guanxiangkai.redis.plus.autoconfigure;

import io.github.guanxiangkai.redis.plus.autoconfigure.cache.RedisPlusCacheAutoConfiguration;
import io.github.guanxiangkai.redis.plus.autoconfigure.core.RedisPlusCoreAutoConfiguration;
import io.github.guanxiangkai.redis.plus.autoconfigure.datasource.RedisPlusDataSourceAutoConfiguration;
import io.github.guanxiangkai.redis.plus.autoconfigure.enhance.RedisPlusEnhanceAutoConfiguration;
import io.github.guanxiangkai.redis.plus.autoconfigure.lock.RedisPlusLockAutoConfiguration;
import io.github.guanxiangkai.redis.plus.autoconfigure.queue.RedisPlusQueueAutoConfiguration;
import io.github.guanxiangkai.redis.plus.autoconfigure.ratelimit.RedisPlusRateLimitAutoConfiguration;
import io.github.guanxiangkai.redis.plus.cache.ThreeLevelCacheTemplate;
import io.github.guanxiangkai.redis.plus.cache.spi.LocalCacheProvider;
import io.github.guanxiangkai.redis.plus.datasource.MultiRedisConnectionFactory;
import io.github.guanxiangkai.redis.plus.core.redis.RedisBackend;
import io.github.guanxiangkai.redis.plus.core.script.RedisScriptExecutor;
import io.github.guanxiangkai.redis.plus.lock.aop.LockAspect;
import io.github.guanxiangkai.redis.plus.queue.RedisQueueFactory;
import io.github.guanxiangkai.redis.plus.ratelimit.aop.RateLimitAspect;
import io.github.guanxiangkai.redis.plus.ratelimit.spi.RateLimitKeyResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisPlusAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RedisPlusCoreAutoConfiguration.class,
                    RedisPlusLockAutoConfiguration.class,
                    RedisPlusCacheAutoConfiguration.class,
                    RedisPlusRateLimitAutoConfiguration.class,
                    RedisPlusQueueAutoConfiguration.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withPropertyValues("redis-plus.lock.enabled=false");

    @Test
    void lockDisabled_doesNotRegisterLockAspect() {
        contextRunner
                .withPropertyValues("redis-plus.lock.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(LockAspect.class));
    }

    @Test
    void queueDisabled_doesNotRegisterQueueFactory() {
        contextRunner
                .withPropertyValues("redis-plus.queue.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RedisQueueFactory.class));
    }

    @Test
    void userLocalCacheProvider_overridesDefaultProvider() {
        contextRunner
                .withUserConfiguration(CustomCacheProviderConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(LocalCacheProvider.class)
                        .getBean(LocalCacheProvider.class)
                        .isSameAs(context.getBean("customLocalCacheProvider")));
    }

    @Test
    void ratelimitAutoConfiguration_registersDefaultKeyResolverAndAspect() {
        contextRunner
                .run(context -> assertThat(context)
                        .hasSingleBean(RateLimitKeyResolver.class)
                        .hasSingleBean(RateLimitAspect.class));
    }

    @Test
    void configuredSources_createsRoutingFactoryBeforeRedisTemplateAndCache() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataRedisAutoConfiguration.class,
                        RedisPlusCoreAutoConfiguration.class,
                        RedisPlusDataSourceAutoConfiguration.class,
                        RedisPlusCacheAutoConfiguration.class))
                .withPropertyValues(
                        "spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.port=6379",
                        "redis-plus.datasource.sources.primary.host=127.0.0.1",
                        "redis-plus.datasource.sources.primary.port=6379",
                        "redis-plus.datasource.sources.primary.database=0")
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(MultiRedisConnectionFactory.class)
                            .hasSingleBean(StringRedisTemplate.class)
                            .hasSingleBean(ThreeLevelCacheTemplate.class);
                    assertThat(context.getBean(MultiRedisConnectionFactory.class).sourceNames())
                            .containsExactly("primary");
                    assertThat(context.getBean(StringRedisTemplate.class).getConnectionFactory())
                            .isSameAs(context.getBean(MultiRedisConnectionFactory.class));
                });
    }

    @Test
    void singleSource_wrapsBootFactoryWithoutCircularDependency() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataRedisAutoConfiguration.class,
                        RedisPlusCoreAutoConfiguration.class,
                        RedisPlusDataSourceAutoConfiguration.class,
                        RedisPlusCacheAutoConfiguration.class,
                        RedisPlusEnhanceAutoConfiguration.class))
                .withUserConfiguration(UnqualifiedRedisConsumerConfiguration.class)
                .withPropertyValues(
                        "spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.port=6379")
                .run(context -> {
                    assertThat(context)
                            .hasNotFailed()
                            .hasSingleBean(MultiRedisConnectionFactory.class)
                            .hasSingleBean(StringRedisTemplate.class)
                            .hasSingleBean(RedisBackend.class)
                            .hasSingleBean(RedisScriptExecutor.class)
                            .hasSingleBean(ThreeLevelCacheTemplate.class)
                            .hasSingleBean(RedisConnectionConsumer.class)
                            .hasBean("defaultBloomFilter");

                    RedisConnectionFactory bootFactory = context.getBean(
                            "redisConnectionFactory", RedisConnectionFactory.class);
                    MultiRedisConnectionFactory routingFactory = context.getBean(
                            MultiRedisConnectionFactory.class);

                    assertThat(routingFactory.determine()).isSameAs(bootFactory);
                    assertThat(context.getBean(StringRedisTemplate.class).getConnectionFactory())
                            .isSameAs(bootFactory);
                    assertThat(context.getBean(RedisConnectionConsumer.class).factory())
                            .isSameAs(bootFactory);
                });
    }

    @Test
    void applicationPrimaryFactory_coexistsWithSingleSourceWrapper() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataRedisAutoConfiguration.class,
                        RedisPlusCoreAutoConfiguration.class,
                        RedisPlusDataSourceAutoConfiguration.class,
                        RedisPlusCacheAutoConfiguration.class,
                        RedisPlusEnhanceAutoConfiguration.class))
                .withUserConfiguration(ApplicationRedisConfiguration.class)
                .run(context -> {
                    assertThat(context)
                            .hasNotFailed()
                            .hasSingleBean(MultiRedisConnectionFactory.class)
                            .hasSingleBean(RedisBackend.class)
                            .hasSingleBean(RedisScriptExecutor.class)
                            .hasSingleBean(ThreeLevelCacheTemplate.class)
                            .hasBean("defaultBloomFilter");

                    RedisConnectionFactory applicationFactory = context.getBean(
                            "redisConnectionFactory", RedisConnectionFactory.class);
                    MultiRedisConnectionFactory routingFactory = context.getBean(
                            MultiRedisConnectionFactory.class);

                    assertThat(routingFactory.determine()).isSameAs(applicationFactory);
                    assertThat(context.getBean(StringRedisTemplate.class).getConnectionFactory())
                            .isSameAs(applicationFactory);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomCacheProviderConfiguration {

        @Bean
        LocalCacheProvider customLocalCacheProvider() {
            return new TestLocalCacheProvider();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationRedisConfiguration {

        @Bean("redisConnectionFactory")
        @Primary
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }

        @Bean("authRedisConnectionFactory")
        RedisConnectionFactory authRedisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }

        @Bean("stringRedisTemplate")
        @Primary
        StringRedisTemplate stringRedisTemplate(
                @Qualifier("redisConnectionFactory") RedisConnectionFactory connectionFactory) {
            return new StringRedisTemplate(connectionFactory);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UnqualifiedRedisConsumerConfiguration {

        @Bean
        RedisConnectionConsumer redisConnectionConsumer(RedisConnectionFactory factory) {
            return new RedisConnectionConsumer(factory);
        }
    }

    record RedisConnectionConsumer(RedisConnectionFactory factory) {
    }

    @SuppressWarnings("NullAway")
    static class TestLocalCacheProvider implements LocalCacheProvider {
        private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

        @Override
        public Object get(String key) {
            return store.get(key);
        }

        @Override
        public void put(String key, Object value, Duration ttl) {
            store.put(key, value);
        }

        @Override
        public void evict(String key) {
            store.remove(key);
        }

        @Override
        public void clear() {
            store.clear();
        }

        @Override
        public void clearByPrefix(String keyPrefix) {
            store.keySet().removeIf(k -> k.startsWith(keyPrefix));
        }
    }
}
