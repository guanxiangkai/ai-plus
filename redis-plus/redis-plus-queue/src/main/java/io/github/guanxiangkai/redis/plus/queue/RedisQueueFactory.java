package io.github.guanxiangkai.redis.plus.queue;

import io.github.guanxiangkai.redis.plus.core.async.RedisPlusAsyncExecutor;
import io.github.guanxiangkai.redis.plus.core.script.DefaultRedisScriptExecutor;
import io.github.guanxiangkai.redis.plus.core.script.RedisScriptExecutor;
import io.github.guanxiangkai.redis.plus.core.serializer.ValueSerializer;
import io.github.guanxiangkai.redis.plus.queue.impl.RedisListQueue;
import io.github.guanxiangkai.redis.plus.queue.impl.RedisStreamQueue;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Redis 队列实例工厂，同时实现 {@link SmartLifecycle} 以支持 Spring 容器优雅停机。
 *
 * <p>工厂持有所有已创建队列实例的引用；容器关闭时自动调用各队列的 {@link RedisListQueue#stop()} /
 * {@link RedisStreamQueue#stop()}，防止后台消费线程在应用下线后继续运行。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class RedisQueueFactory implements SmartLifecycle {

    private final StringRedisTemplate redisTemplate;
    private final RedisScriptExecutor scriptExecutor;
    private final ValueSerializer serializer;
    private final String listKeyPrefix;
    private final String streamKeyPrefix;
    private final String defaultConsumerGroup;
    private final RedisPlusAsyncExecutor asyncExecutor;
    private final QueueRuntimePolicy policy;

    private final CopyOnWriteArrayList<RedisListQueue<?>> listQueues = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<RedisStreamQueue<?>> streamQueues = new CopyOnWriteArrayList<>();

    public RedisQueueFactory(StringRedisTemplate redisTemplate,
                             ValueSerializer serializer,
                             RedisScriptExecutor scriptExecutor,
                             String listKeyPrefix,
                             String streamKeyPrefix,
                             String defaultConsumerGroup,
                             RedisPlusAsyncExecutor asyncExecutor,
                             QueueRuntimePolicy policy) {
        this.redisTemplate = redisTemplate;
        this.scriptExecutor = scriptExecutor != null ? scriptExecutor : new DefaultRedisScriptExecutor(redisTemplate);
        this.serializer = serializer;
        this.listKeyPrefix = listKeyPrefix;
        this.streamKeyPrefix = streamKeyPrefix;
        this.defaultConsumerGroup = defaultConsumerGroup;
        this.asyncExecutor = asyncExecutor;
        this.policy = policy != null ? policy : QueueRuntimePolicy.defaults();
    }

    public <T> SimpleQueue<T> createListQueue(String queueName, Class<T> messageType) {
        RedisListQueue<T> queue = new RedisListQueue<>(queueName, listKeyPrefix, messageType,
                redisTemplate, serializer, asyncExecutor, policy);
        listQueues.add(queue);
        return queue;
    }

    public <T> AckQueue<T> createStreamQueue(String queueName, Class<T> messageType) {
        RedisStreamQueue<T> queue = new RedisStreamQueue<>(queueName, defaultConsumerGroup,
                streamKeyPrefix, messageType, redisTemplate, serializer, scriptExecutor, asyncExecutor,
                policy);
        streamQueues.add(queue);
        return queue;
    }

    public <T> AckQueue<T> createStreamQueue(String queueName,
                                                     String consumerGroup,
                                                     Class<T> messageType) {
        RedisStreamQueue<T> queue = new RedisStreamQueue<>(queueName, consumerGroup,
                streamKeyPrefix, messageType, redisTemplate, serializer, scriptExecutor, asyncExecutor,
                policy);
        streamQueues.add(queue);
        return queue;
    }

    // ── SmartLifecycle ────────────────────────────────────────────────────────

    /** 队列在调用 {@code subscribe()} 时自行启动，此处为空操作。 */
    @Override
    public void start() {
        // no-op: queues start when subscribe() is called
    }

    /** 容器关闭时停止所有已追踪队列。 */
    @Override
    public void stop() {
        listQueues.forEach(RedisListQueue::stop);
        streamQueues.forEach(RedisStreamQueue::stop);
    }

    @Override
    public boolean isRunning() {
        return listQueues.stream().anyMatch(RedisListQueue::isRunning)
                || streamQueues.stream().anyMatch(RedisStreamQueue::isRunning);
    }

}
