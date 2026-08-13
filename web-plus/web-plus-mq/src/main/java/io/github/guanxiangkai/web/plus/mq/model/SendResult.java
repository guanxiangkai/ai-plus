package io.github.guanxiangkai.web.plus.mq.model;

/**
 * 消息发送结果
 * <p>
 * {@link io.github.guanxiangkai.web.plus.mq.producer.MessageProducer} 的返回值，
 * 明确携带发送成功/失败信息，避免调用方无法区分成功与失败。
 * </p>
 *
 * @param messageId 消息唯一 ID
 * @param success   是否发送成功
 * @param topic     目标 topic
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public record SendResult(String messageId, boolean success, String topic) {

    public static SendResult ok(String messageId, String topic) {
        return new SendResult(messageId, true, topic);
    }
}
