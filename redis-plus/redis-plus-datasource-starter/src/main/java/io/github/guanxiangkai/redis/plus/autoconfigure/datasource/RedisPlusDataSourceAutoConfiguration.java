package io.github.guanxiangkai.redis.plus.autoconfigure.datasource;

import io.github.guanxiangkai.redis.plus.autoconfigure.core.RedisPlusCoreAutoConfiguration;
import io.github.guanxiangkai.redis.plus.autoconfigure.properties.RedisPlusDataSourceProperties;
import io.github.guanxiangkai.redis.plus.datasource.MultiRedisConnectionFactory;
import io.github.guanxiangkai.redis.plus.datasource.RedisRouteStrategy;
import io.github.guanxiangkai.redis.plus.datasource.aop.RedisDSAspect;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Primary;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多数据源自动装配
 *
 * <p>优先级：
 * <ol>
 *   <li>用户自定义 {@link MultiRedisConnectionFactory} Bean（最高优先级，直接使用）</li>
 *   <li>YAML 配置了 {@code redis-plus.datasource.sources.*}（从配置自动构建多数据源）</li>
 *   <li>回退到包装 {@code spring.data.redis.*} 单数据源（兜底）</li>
 * </ol>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@AutoConfiguration(after = {RedisPlusCoreAutoConfiguration.class, DataRedisAutoConfiguration.class})
@ConditionalOnProperty(prefix = "redis-plus.datasource", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedisPlusDataSourceProperties.class)
@SuppressWarnings("NullAway")
public class RedisPlusDataSourceAutoConfiguration {

    /**
     * 注册数据源路由切面（@RedisDS 注解支持）。
     * MultiRedisConnectionFactory 始终存在，切面始终激活；单数据源时 @RedisDS 路由到 primary，无副作用。
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisDSAspect redisDSAspect() {
        return new RedisDSAspect();
    }

    /**
     * 根据显式配置构建多数据源路由工厂。
     *
     * <p>只有存在 {@code redis-plus.datasource.sources.*} 时才成为主连接工厂，
     * 确保 {@code @RedisDS} 能透明切换应用中的 RedisTemplate。
     */
    @Bean(name = "multiRedisConnectionFactory", destroyMethod = "destroy")
    @Primary
    @Conditional(ConfiguredSourcesCondition.class)
    @ConditionalOnMissingBean(MultiRedisConnectionFactory.class)
    public MultiRedisConnectionFactory configuredMultiRedisConnectionFactory(
            RedisPlusDataSourceProperties properties,
            ObjectProvider<RedisRouteStrategy> routeStrategyProvider) {
        Map<String, RedisPlusDataSourceProperties.RedisSourceProperties> sources = properties.getSources();
        RedisRouteStrategy routeStrategy = routeStrategyProvider.getIfAvailable();
        Map<String, RedisConnectionFactory> factories = new LinkedHashMap<>();
        sources.forEach((name, source) -> factories.put(name, buildLettuceFactory(source)));
        return new MultiRedisConnectionFactory(
                factories,
                properties.getPrimary(),
                properties.isStrict(),
                routeStrategy);
    }

    /**
     * 包装 Spring Boot 单数据源连接工厂。
     *
     * <p>包装工厂作为后备候选，普通按类型注入仍使用应用的默认连接工厂，
     * 并允许认证等专用连接并存。
     */
    @Bean(name = "multiRedisConnectionFactory", destroyMethod = "destroy")
    @Fallback
    @Conditional(SingleSourceCondition.class)
    @ConditionalOnMissingBean(MultiRedisConnectionFactory.class)
    public MultiRedisConnectionFactory singleSourceMultiRedisConnectionFactory(
            @Qualifier("redisConnectionFactory") ObjectProvider<RedisConnectionFactory> defaultFactoryProvider,
            ObjectProvider<RedisRouteStrategy> routeStrategyProvider) {
        RedisConnectionFactory defaultFactory = defaultFactoryProvider.getIfAvailable();
        if (defaultFactory == null) {
            throw new IllegalStateException(
                    "未找到 Redis 连接工厂。请配置 spring.data.redis.* 使用单数据源，" +
                            "或配置 redis-plus.datasource.sources.* 使用多数据源。");
        }
        return new MultiRedisConnectionFactory(
                Map.of("primary", defaultFactory),
                "primary",
                false,
                routeStrategyProvider.getIfAvailable());
    }

    private static boolean hasConfiguredSources(ConditionContext context) {
        return Binder.get(context.getEnvironment())
                .bind(
                        "redis-plus.datasource.sources",
                        Bindable.mapOf(
                                String.class,
                                RedisPlusDataSourceProperties.RedisSourceProperties.class))
                .map(sources -> !sources.isEmpty())
                .orElse(false);
    }

    private static final class ConfiguredSourcesCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return hasConfiguredSources(context);
        }
    }

    private static final class SingleSourceCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !hasConfiguredSources(context);
        }
    }

    /**
     * 根据单个数据源属性构建 {@link LettuceConnectionFactory}。
     * 调用 {@code afterPropertiesSet()} 完成连接工厂初始化（等同于 Spring 容器生命周期回调）。
     */
    private LettuceConnectionFactory buildLettuceFactory(RedisPlusDataSourceProperties.RedisSourceProperties src) {
        RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
        standaloneConfig.setHostName(src.getHost());
        standaloneConfig.setPort(src.getPort());
        standaloneConfig.setDatabase(src.getDatabase());
        if (src.getPassword() != null && !src.getPassword().isBlank()) {
            standaloneConfig.setPassword(RedisPassword.of(src.getPassword()));
        }

        LettuceConnectionFactory factory;
        RedisPlusDataSourceProperties.PoolProperties pool = src.getPool();

        if (pool.isEnabled()) {
            GenericObjectPoolConfig<io.lettuce.core.api.StatefulConnection<?, ?>> poolConfig =
                    new GenericObjectPoolConfig<>();
            poolConfig.setMaxTotal(pool.getMaxActive());
            poolConfig.setMaxIdle(pool.getMaxIdle());
            poolConfig.setMinIdle(pool.getMinIdle());
            poolConfig.setMaxWait(pool.getMaxWait());

            LettucePoolingClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                    .commandTimeout(src.getTimeout())
                    .poolConfig(poolConfig)
                    .build();
            factory = new LettuceConnectionFactory(standaloneConfig, clientConfig);
        } else {
            LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                    .commandTimeout(src.getTimeout())
                    .build();
            factory = new LettuceConnectionFactory(standaloneConfig, clientConfig);
        }

        factory.afterPropertiesSet();
        return factory;
    }
}
