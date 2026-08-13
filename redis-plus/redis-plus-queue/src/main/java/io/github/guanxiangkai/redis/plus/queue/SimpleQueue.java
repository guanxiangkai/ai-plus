package io.github.guanxiangkai.redis.plus.queue;

/**
 * Queue whose messages are considered consumed once dequeued.
 */
public interface SimpleQueue<T> extends MessageQueue<T> {
}
