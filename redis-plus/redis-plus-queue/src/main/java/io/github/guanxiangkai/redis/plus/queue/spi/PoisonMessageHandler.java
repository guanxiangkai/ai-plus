package io.github.guanxiangkai.redis.plus.queue.spi;

import io.github.guanxiangkai.redis.plus.queue.QueuePoisonMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 无法反序列化的队列消息隔离 SPI。
 *
 * <p>Stream 队列仅在处理器正常返回后确认消息；处理器抛出异常时消息保留在 PEL 中，
 * 便于后续恢复。生产环境可覆盖默认实现，将原始载荷写入受控隔离存储。
 *
 * @author guanxiangkai
 * @since 1.0.2
 */
@FunctionalInterface
public interface PoisonMessageHandler {

    /**
     * 处理毒消息。
     *
     * @param message 毒消息上下文
     */
    void handle(QueuePoisonMessage message);

    /** 返回不输出原始载荷的默认日志处理器。 */
    static PoisonMessageHandler logAndDiscard() {
        Logger logger = LoggerFactory.getLogger(PoisonMessageHandler.class);
        return message -> logger.error(
                "[redis-plus] 隔离毒消息，type={}, queue={}, id={}, reason={}",
                message.storageType(), message.queueName(), message.messageId(), message.reason(), message.cause());
    }
}
