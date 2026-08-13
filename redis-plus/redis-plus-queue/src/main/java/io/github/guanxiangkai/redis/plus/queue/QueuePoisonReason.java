package io.github.guanxiangkai.redis.plus.queue;

/**
 * 队列毒消息原因。
 *
 * @author guanxiangkai
 * @since 1.0.2
 */
public enum QueuePoisonReason {

    /** 消息缺少协议要求的载荷字段。 */
    MISSING_PAYLOAD,

    /** 消息不符合队列传输信封格式。 */
    INVALID_ENVELOPE,

    /** 消息载荷无法反序列化为目标类型。 */
    DESERIALIZATION_FAILED
}
