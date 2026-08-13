package io.github.guanxiangkai.redis.plus.queue;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Redis 消息队列统一抽象
 *
 * <p>支持基于 Redis List（简单队列）和 Redis Stream（消费组模式）的两种实现。
 *
 * @param <T> 消息类型
 */
public interface MessageQueue<T> {

    /**
     * 发送消息。
     *
     * @param message 消息对象
     * @return 消息 ID
     */
    String send(T message);

    /**
     * 同步接收一条消息（阻塞等待）。
     *
     * @param timeout 最大等待时间；{@link Duration#ZERO} 表示非阻塞
     * @return 消息交付对象；超时则返回 {@code null}
     */
    QueueDelivery<T> receive(Duration timeout);

    /**
     * 注册异步消费者。
     *
     * @param consumer 消息处理回调
     */
    QueueSubscription subscribe(Consumer<T> consumer);

    /**
     * 获取队列名称。
     */
    String getQueueName();

    /**
     * 获取队列当前积压消息数量。
     */
    long size();

}
