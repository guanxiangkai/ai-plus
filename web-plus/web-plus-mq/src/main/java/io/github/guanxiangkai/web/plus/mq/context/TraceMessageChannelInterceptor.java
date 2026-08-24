package io.github.guanxiangkai.web.plus.mq.context;

import io.github.guanxiangkai.web.plus.core.constant.WebPlusConstants;
import io.github.guanxiangkai.web.plus.core.context.RequestContext;
import io.github.guanxiangkai.web.plus.core.context.RequestContextThreadLocalAccessor;
import io.github.guanxiangkai.web.plus.core.spi.TraceIdGenerator;
import io.github.guanxiangkai.web.plus.core.trace.TraceId;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 在 Spring Cloud Stream 消费处理器边界恢复并清理 TraceId 请求上下文。
 *
 * <p>标准 {@code traceparent} 由 Spring Cloud Stream Observation 处理；本拦截器保证自定义
 * {@code X-Trace-Id} 与 RequestContext/MDC 同步，并在成功或异常后恢复处理线程原状态。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class TraceMessageChannelInterceptor implements ExecutorChannelInterceptor {

    private final RequestContextThreadLocalAccessor contextAccessor;
    private final TraceIdGenerator traceIdGenerator;
    private final ThreadLocal<Deque<PreviousContext>> previousContexts =
            ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 创建消息消费 TraceId 上下文拦截器。
     *
     * @param contextAccessor 请求上下文适配器
     * @param traceIdGenerator TraceId 当前值与生成策略
     */
    public TraceMessageChannelInterceptor(
            RequestContextThreadLocalAccessor contextAccessor,
            TraceIdGenerator traceIdGenerator) {
        this.contextAccessor = contextAccessor;
        this.traceIdGenerator = traceIdGenerator;
    }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        String traceId = traceIdGenerator.currentTraceId();
        if (!TraceId.isValid(traceId)) {
            traceId = TraceId.fromHeader(message.getHeaders().get(WebPlusConstants.TRACE_ID_HEADER));
        }
        if (!TraceId.isValid(traceId)) {
            traceId = traceIdGenerator.generate();
        }
        if (!TraceId.isValid(traceId)) {
            throw new IllegalStateException("TraceIdGenerator 返回了非法 TraceId");
        }

        previousContexts.get().push(new PreviousContext(contextAccessor.getValue()));
        Object topicHeader = message.getHeaders().get("topic");
        String topic = topicHeader == null ? null : String.valueOf(topicHeader);
        contextAccessor.setValue(new RequestContext(
                traceId,
                topic == null ? "mq" : "mq:" + topic,
                "CONSUME",
                null,
                null,
                System.currentTimeMillis()
        ));
        return message;
    }

    @Override
    public void afterMessageHandled(
            Message<?> message,
            MessageChannel channel,
            MessageHandler handler,
            Exception exception) {
        Deque<PreviousContext> stack = previousContexts.get();
        PreviousContext previous = stack.poll();
        if (previous == null || previous.value() == null) {
            contextAccessor.setValue();
        } else {
            contextAccessor.restore(previous.value());
        }
        if (stack.isEmpty()) {
            previousContexts.remove();
        }
    }

    private record PreviousContext(RequestContext value) {
    }
}
