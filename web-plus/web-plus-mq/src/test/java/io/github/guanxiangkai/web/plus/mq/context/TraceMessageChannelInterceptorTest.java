package io.github.guanxiangkai.web.plus.mq.context;

import io.github.guanxiangkai.web.plus.core.constant.WebPlusConstants;
import io.github.guanxiangkai.web.plus.core.context.RequestContext;
import io.github.guanxiangkai.web.plus.core.context.RequestContextHolder;
import io.github.guanxiangkai.web.plus.core.context.RequestContextThreadLocalAccessor;
import io.github.guanxiangkai.web.plus.core.spi.TraceIdGenerator;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TraceMessageChannelInterceptorTest {

    @Test
    void restoresMessageTraceIdAndCleansConsumerThread() {
        RequestContextThreadLocalAccessor accessor = new RequestContextThreadLocalAccessor();
        TraceMessageChannelInterceptor interceptor = new TraceMessageChannelInterceptor(
                accessor, () -> "generated-trace");
        var message = MessageBuilder.withPayload("payload")
                .setHeader(WebPlusConstants.TRACE_ID_HEADER, "trace-consumer")
                .setHeader("topic", "orders.created")
                .build();
        MessageChannel channel = mock(MessageChannel.class);
        MessageHandler handler = mock(MessageHandler.class);

        interceptor.beforeHandle(message, channel, handler);

        assertThat(RequestContextHolder.getTraceId()).isEqualTo("trace-consumer");
        assertThat(MDC.get(WebPlusConstants.MDC_TRACE_ID)).isEqualTo("trace-consumer");

        interceptor.afterMessageHandled(message, channel, handler, null);

        assertThat(RequestContextHolder.get()).isNull();
        assertThat(MDC.get(WebPlusConstants.MDC_TRACE_ID)).isNull();
    }

    @Test
    void restoresPreviousContextAfterNestedMessageHandling() {
        RequestContextThreadLocalAccessor accessor = new RequestContextThreadLocalAccessor();
        TraceMessageChannelInterceptor interceptor = new TraceMessageChannelInterceptor(
                accessor, () -> "generated-trace");
        RequestContext parent = new RequestContext(
                "trace-parent", "/source", "POST", null, null, System.currentTimeMillis());
        accessor.setValue(parent);
        var message = MessageBuilder.withPayload("payload")
                .setHeader(WebPlusConstants.TRACE_ID_HEADER, "trace-child")
                .build();
        MessageChannel channel = mock(MessageChannel.class);
        MessageHandler handler = mock(MessageHandler.class);

        try {
            interceptor.beforeHandle(message, channel, handler);
            interceptor.afterMessageHandled(message, channel, handler, null);

            assertThat(RequestContextHolder.get()).isEqualTo(parent);
            assertThat(MDC.get(WebPlusConstants.MDC_TRACE_ID)).isEqualTo("trace-parent");
        } finally {
            accessor.setValue();
        }
    }

    @Test
    void standardTraceContextOverridesUntrustedMessageHeader() {
        RequestContextThreadLocalAccessor accessor = new RequestContextThreadLocalAccessor();
        TraceIdGenerator generator = new TraceIdGenerator() {
            @Override
            public String currentTraceId() {
                return "trace-standard";
            }

            @Override
            public String generate() {
                return "trace-generated";
            }
        };
        TraceMessageChannelInterceptor interceptor = new TraceMessageChannelInterceptor(accessor, generator);
        var message = MessageBuilder.withPayload("payload")
                .setHeader(WebPlusConstants.TRACE_ID_HEADER, "trace-untrusted")
                .build();
        MessageChannel channel = mock(MessageChannel.class);
        MessageHandler handler = mock(MessageHandler.class);

        try {
            interceptor.beforeHandle(message, channel, handler);
            assertThat(RequestContextHolder.getTraceId()).isEqualTo("trace-standard");
        } finally {
            interceptor.afterMessageHandled(message, channel, handler, null);
        }
    }
}
