package io.github.guanxiangkai.redis.plus.autoconfigure.queue;

import io.github.guanxiangkai.redis.plus.autoconfigure.cache.RedisPlusCacheAutoConfiguration;
import io.github.guanxiangkai.redis.plus.autoconfigure.governance.RedisPlusGovernanceAutoConfiguration;
import io.github.guanxiangkai.redis.plus.autoconfigure.properties.RedisPlusQueueProperties;
import io.github.guanxiangkai.redis.plus.core.async.RedisPlusAsyncExecutor;
import io.github.guanxiangkai.redis.plus.core.observation.RedisPlusObserver;
import io.github.guanxiangkai.redis.plus.core.script.RedisScriptExecutor;
import io.github.guanxiangkai.redis.plus.core.serializer.ValueSerializer;
import io.github.guanxiangkai.redis.plus.queue.QueueRuntimePolicy;
import io.github.guanxiangkai.redis.plus.queue.RedisQueueFactory;
import io.github.guanxiangkai.redis.plus.queue.spi.DeadLetterHandler;
import io.github.guanxiangkai.redis.plus.queue.spi.PoisonMessageHandler;
import io.github.guanxiangkai.redis.plus.queue.spi.QueueRetryStrategy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 队列能力自动装配。
 *
 * <p>仅在 Redis 运行时存在且 {@code redis-plus.queue.enabled} 启用时注册队列工厂与默认处理器。
 * 用户提供同类型 Bean 时自动退让。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@AutoConfiguration(after = {RedisPlusCacheAutoConfiguration.class, RedisPlusGovernanceAutoConfiguration.class})
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "redis-plus.queue", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedisPlusQueueProperties.class)
public class RedisPlusQueueAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DeadLetterHandler.class)
    public DeadLetterHandler<Object> redisQueueDeadLetterHandler() {
        return DeadLetterHandler.logAndDiscard();
    }

    @Bean
    @ConditionalOnMissingBean(PoisonMessageHandler.class)
    public PoisonMessageHandler redisQueuePoisonMessageHandler() {
        return PoisonMessageHandler.logAndDiscard();
    }

    @Bean("redisQueueFactory")
    @ConditionalOnMissingBean(RedisQueueFactory.class)
    public RedisQueueFactory redisQueueFactory(StringRedisTemplate redisTemplate,
                                               ValueSerializer serializer,
                                               RedisScriptExecutor scriptExecutor,
                                               RedisPlusAsyncExecutor asyncExecutor,
                                               RedisPlusObserver observer,
                                               DeadLetterHandler<Object> deadLetterHandler,
                                               PoisonMessageHandler poisonMessageHandler,
                                               RedisPlusQueueProperties properties) {
        RedisPlusQueueProperties q = properties;
        String base = normalizePrefix(q.getKeyPrefix());
        return new RedisQueueFactory(
                redisTemplate, serializer, scriptExecutor,
                base + "list:",
                base + "stream:",
                q.getDefaultConsumerGroup(),
                asyncExecutor,
                new QueueRuntimePolicy(
                        QueueRetryStrategy.fixed(java.time.Duration.ofSeconds(1), q.getMaxRetryAttempts()),
                        deadLetterHandler,
                        q.getPollTimeout(),
                        q.getBatchSize(),
                        observer,
                        q.isReclaimOnStart(),
                        q.getPendingReclaimIdleTime(),
                        q.getMaxStreamLength(),
                        poisonMessageHandler,
                        q.getReadFailure().toPolicy()));
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "redis-plus:queue:";
        }
        return value.endsWith(":") ? value : value + ":";
    }
}
