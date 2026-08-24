package io.github.guanxiangkai.web.plus.mq.producer;

import io.github.guanxiangkai.web.plus.mq.exception.MessageSendException;
import io.github.guanxiangkai.web.plus.core.constant.WebPlusConstants;
import io.github.guanxiangkai.web.plus.core.context.RequestContextHolder;
import io.github.guanxiangkai.web.plus.core.trace.TraceId;
import io.github.guanxiangkai.web.plus.mq.model.MqMessage;
import io.github.guanxiangkai.web.plus.mq.model.SendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Async;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 消息生产者服务
 * <p>
 * 基于 Spring Cloud Stream {@link StreamBridge} 实现。
 * 与 Kafka / RabbitMQ 等具体 Binder 解耦，通过 YAML 配置绑定目标。
 * </p>
 * <p>
 * 发送失败时抛出 {@link MessageSendException}，调用方应捕获并决定重试/告警策略，
 * 禁止静默吞噬（即：不允许 catch 后只打 warn 日志然后继续）。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class MessageProducer {

    private final StreamBridge streamBridge;

    /**
     * 发送普通消息
     *
     * @param topic   目标 binding 名（对应 Kafka topic）
     * @param payload 消息负载
     * @return {@link SendResult}，messageId 用于定位单条消息
     * @throws MessageSendException 发送失败时抛出，调用方必须处理
     */
    public <T> SendResult send(String topic, T payload) {
        String messageId = generateMessageId();
        MqMessage<T> mqMessage = MqMessage.of(messageId, topic, payload);
        return doSend(topic, mqMessage);
    }

    /**
     * 发送带标签的消息
     *
     * @throws MessageSendException 发送失败时抛出
     */
    public <T> SendResult send(String topic, String tag, T payload) {
        String messageId = generateMessageId();
        MqMessage<T> mqMessage = MqMessage.create(
                messageId, topic, tag, MqMessage.TYPE_NORMAL, payload
        );
        return doSend(topic, mqMessage);
    }

    /**
     * 异步发送消息（使用虚拟线程）
     * <p>
     * 返回的 {@link CompletableFuture} 在发送失败时会以 {@link MessageSendException} 完成，
     * 调用方应通过 {@code .exceptionally()} 或 {@code .whenComplete()} 处理异常。
     * </p>
     */
    @Async
    public <T> CompletableFuture<SendResult> sendAsync(String topic, T payload) {
        try {
            return CompletableFuture.completedFuture(send(topic, payload));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /**
     * 执行发送（Spring Cloud Stream → StreamBridge）
     *
     * @throws MessageSendException 当 streamBridge 返回 false 或抛出异常时
     */
    private <T> SendResult doSend(String topic, MqMessage<T> mqMessage) {
        MessageBuilder<MqMessage<T>> messageBuilder = MessageBuilder
                .withPayload(mqMessage)
                .setHeader("messageId", mqMessage.messageId())
                .setHeader("topic", topic);
        String traceId = RequestContextHolder.getTraceId();
        if (TraceId.isValid(traceId)) {
            messageBuilder.setHeader(WebPlusConstants.TRACE_ID_HEADER, traceId);
        }
        Message<MqMessage<T>> message = messageBuilder.build();

        boolean sent;
        try {
            sent = streamBridge.send(topic, message);
        } catch (Exception e) {
            log.error("消息投递异常: topic={}, messageId={}", topic, mqMessage.messageId(), e);
            throw new MessageSendException(topic, mqMessage.messageId(), e);
        }

        if (!sent) {
            log.error("消息发送失败（StreamBridge 返回 false）: topic={}, messageId={}", topic, mqMessage.messageId());
            throw new MessageSendException(topic, mqMessage.messageId(), "StreamBridge 返回 false，消息未被接受");
        }

        log.debug("消息发送成功: topic={}, messageId={}", topic, mqMessage.messageId());
        return SendResult.ok(mqMessage.messageId(), topic);
    }

    private String generateMessageId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
