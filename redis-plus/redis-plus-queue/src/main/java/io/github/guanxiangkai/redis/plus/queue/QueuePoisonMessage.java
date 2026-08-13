package io.github.guanxiangkai.redis.plus.queue;

import java.util.Objects;

/**
 * 无法进入业务消费者的原始队列消息。
 *
 * <p>默认日志处理器不会输出 {@link #rawPayload()}，自定义隔离处理器可将其写入受控死信存储。
 *
 * @param storageType 队列存储类型
 * @param queueName 队列名称
 * @param messageId 消息标识
 * @param rawPayload 原始载荷；缺少载荷时为 {@code null}
 * @param reason 毒消息原因
 * @param cause 原始异常
 * @author guanxiangkai
 * @since 1.0.2
 */
public record QueuePoisonMessage(
        QueueStorageType storageType,
        String queueName,
        String messageId,
        String rawPayload,
        QueuePoisonReason reason,
        Exception cause
) {

    /** 无法从消息信封解析标识时使用的稳定占位值。 */
    public static final String UNKNOWN_MESSAGE_ID = "unknown";

    public QueuePoisonMessage {
        Objects.requireNonNull(storageType, "storageType 不能为空");
        queueName = requireText(queueName, "queueName");
        messageId = requireText(messageId, "messageId");
        Objects.requireNonNull(reason, "reason 不能为空");
        Objects.requireNonNull(cause, "cause 不能为空");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
