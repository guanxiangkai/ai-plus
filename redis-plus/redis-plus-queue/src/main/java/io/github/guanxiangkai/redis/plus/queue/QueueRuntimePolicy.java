package io.github.guanxiangkai.redis.plus.queue;

import io.github.guanxiangkai.redis.plus.core.observation.RedisPlusObserver;
import io.github.guanxiangkai.redis.plus.queue.spi.DeadLetterHandler;
import io.github.guanxiangkai.redis.plus.queue.spi.PoisonMessageHandler;
import io.github.guanxiangkai.redis.plus.queue.spi.QueueRetryStrategy;

import java.time.Duration;

/**
 * Redis 队列实现共享的运行策略。
 *
 * @author guanxiangkai
 * @since 1.0.1
 */
public record QueueRuntimePolicy(
        QueueRetryStrategy retryStrategy,
        DeadLetterHandler<Object> deadLetterHandler,
        Duration pollTimeout,
        int batchSize,
        RedisPlusObserver observer,
        boolean reclaimOnStart,
        Duration pendingReclaimIdleTime,
        long maxStreamLength,
        PoisonMessageHandler poisonMessageHandler,
        QueueReadFailurePolicy readFailurePolicy
) {

    public QueueRuntimePolicy {
        retryStrategy = retryStrategy != null ? retryStrategy : QueueRetryStrategy.noRetry();
        deadLetterHandler = deadLetterHandler != null ? deadLetterHandler : DeadLetterHandler.logAndDiscard();
        pollTimeout = pollTimeout != null ? pollTimeout : Duration.ofSeconds(2);
        batchSize = batchSize > 0 ? batchSize : 10;
        observer = observer != null ? observer : RedisPlusObserver.noop();
        pendingReclaimIdleTime = pendingReclaimIdleTime != null ? pendingReclaimIdleTime : Duration.ofMinutes(5);
        maxStreamLength = Math.max(0, maxStreamLength);
        poisonMessageHandler = poisonMessageHandler != null
                ? poisonMessageHandler
                : PoisonMessageHandler.logAndDiscard();
        readFailurePolicy = readFailurePolicy != null
                ? readFailurePolicy
                : QueueReadFailurePolicy.defaults();
    }

    public static QueueRuntimePolicy defaults() {
        return new QueueRuntimePolicy(QueueRetryStrategy.noRetry(), DeadLetterHandler.logAndDiscard(),
                Duration.ofSeconds(2), 10, RedisPlusObserver.noop(), false, Duration.ofMinutes(5), 0,
                PoisonMessageHandler.logAndDiscard(), QueueReadFailurePolicy.defaults());
    }
}
