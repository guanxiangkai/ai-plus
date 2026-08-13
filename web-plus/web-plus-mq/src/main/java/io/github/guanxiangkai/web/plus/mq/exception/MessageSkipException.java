package io.github.guanxiangkai.web.plus.mq.exception;

/**
 * 消息主动跳过异常
 * <p>
 * 业务消息处理器在业务上决定"暂不消费"时抛出此异常，
 * 消息消费者捕获后<b>不提交 Kafka offset</b>，
 * 通过 {@code Acknowledgment.nack()} 延迟重试该消息。
 * <br/>
 * 与普通异常的区别：不打印 ERROR 日志，只打印 DEBUG，避免监控误报。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class MessageSkipException extends RuntimeException {

    public MessageSkipException(String reason) {
        super(reason);
    }
}
