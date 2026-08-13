package io.github.guanxiangkai.redis.plus.autoconfigure.core;

import io.github.guanxiangkai.redis.plus.core.async.DefaultRedisPlusAsyncExecutor;
import io.github.guanxiangkai.redis.plus.core.async.RedisPlusAsyncExecutor;
import io.github.guanxiangkai.redis.plus.core.key.DefaultKeyNamingStrategy;
import io.github.guanxiangkai.redis.plus.core.key.KeyNamingStrategy;
import io.github.guanxiangkai.redis.plus.core.observation.RedisPlusObserver;
import io.github.guanxiangkai.redis.plus.core.redis.RedisBackend;
import io.github.guanxiangkai.redis.plus.core.redis.StringRedisBackend;
import io.github.guanxiangkai.redis.plus.core.script.DefaultRedisScriptExecutor;
import io.github.guanxiangkai.redis.plus.core.script.RedisScriptExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis Plus 核心基础设施自动装配
 *
 * <p>注册以下核心基础 Bean（均为 {@code @ConditionalOnMissingBean}，用户可覆盖）：
 * <ul>
 *   <li>{@link KeyNamingStrategy} — Key 命名策略（默认冒号分隔）</li>
 *   <li>{@link RedisScriptExecutor} — Lua 脚本执行器</li>
 * </ul>
 *
 * <p>同时启用 AspectJ 动态代理（{@code proxyTargetClass = true}），
 * 确保锁、缓存、限流等 AOP 切面对普通类也能正常织入。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@AutoConfiguration(after = DataRedisAutoConfiguration.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class RedisPlusCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(KeyNamingStrategy.class)
    public KeyNamingStrategy keyNamingStrategy() {
        return new DefaultKeyNamingStrategy();
    }

    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(RedisBackend.class)
    public RedisBackend redisBackend(StringRedisTemplate stringRedisTemplate) {
        return new StringRedisBackend(stringRedisTemplate);
    }

    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(RedisBackend.class)
    @ConditionalOnMissingBean(RedisScriptExecutor.class)
    public RedisScriptExecutor redisScriptExecutor(RedisBackend redisBackend) {
        return new DefaultRedisScriptExecutor(redisBackend);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(RedisPlusAsyncExecutor.class)
    public RedisPlusAsyncExecutor redisPlusAsyncExecutor() {
        return new DefaultRedisPlusAsyncExecutor();
    }

    @Bean
    @ConditionalOnMissingBean(RedisPlusObserver.class)
    public RedisPlusObserver redisPlusObserver() {
        return RedisPlusObserver.noop();
    }
}
