package io.github.guanxiangkai.web.plus.mq.model;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

import java.time.LocalDateTime;

/**
 * 消息模型 Record
 * <p>
 * GraalVM JDK 25 + Spring Boot 4 特性：
 * - Java Record（不可变）
 * - @RegisterReflectionForBinding（AOT 支持）
 * - 紧凑型构造器
 * </p>
 * <p>
 * messageType 为字符串，值由 dict 字典管理（字典类型：mq_message_type）。
 * </p>
 *
 * @param <T> 消息负载类型
 * @author guanxiangkai
 * @since 1.0.0
 */
@RegisterReflectionForBinding
public record MqMessage<T>(
        String messageId,
        String topic,
        String tag,
        String messageType,
        T payload,
        LocalDateTime createTime
) {

    /**
     * 默认消息类型
     */
    public static final String TYPE_NORMAL = "NORMAL";

    /**
     * 紧凑型构造器 - 自动设置默认值
     */
    public MqMessage {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
        if (messageType == null || messageType.isBlank()) {
            messageType = TYPE_NORMAL;
        }
    }

    /**
     * 简化工厂方法 - 普通消息
     */
    public static <T> MqMessage<T> of(String messageId, String topic, T payload) {
        return new MqMessage<>(messageId, topic, null, TYPE_NORMAL, payload, null);
    }

    /**
     * 完整工厂方法
     */
    public static <T> MqMessage<T> create(
            String messageId,
            String topic,
            String tag,
            String messageType,
            T payload) {
        return new MqMessage<>(messageId, topic, tag, messageType, payload, null);
    }
}
