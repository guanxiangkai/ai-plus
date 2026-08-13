package io.github.guanxiangkai.web.plus.mq.exception;

import java.io.Serial;

/**
 * 消息发送失败异常
 * <p>
 * 当 {@link io.github.guanxiangkai.web.plus.mq.producer.MessageProducer} 发送消息到 Kafka/MQ 失败时抛出，
 * 调用方可捕获此异常实现重试、告警或降级逻辑。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class MessageSendException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String topic;
    private final String messageId;

    public MessageSendException(String topic, String messageId, String reason) {
        super("消息发送失败: topic=" + topic + ", messageId=" + messageId + ", reason=" + reason);
        this.topic = topic;
        this.messageId = messageId;
    }

    public MessageSendException(String topic, String messageId, Throwable cause) {
        super("消息发送失败: topic=" + topic + ", messageId=" + messageId, cause);
        this.topic = topic;
        this.messageId = messageId;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageId() {
        return messageId;
    }
}
