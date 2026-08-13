package io.github.guanxiangkai.redis.plus.queue;

/**
 * Redis 队列存储类型。
 *
 * @author guanxiangkai
 * @since 1.0.2
 */
public enum QueueStorageType {

    /** Redis List 队列。 */
    LIST,

    /** Redis Stream 消费组队列。 */
    STREAM
}
