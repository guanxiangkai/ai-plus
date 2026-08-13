package io.github.guanxiangkai.redis.plus.queue;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Queue with explicit acknowledgement and pending-message recovery.
 */
public interface AckQueue<T> extends MessageQueue<T> {

    long reclaimPending(Duration idleTime, Consumer<T> consumer);
}
