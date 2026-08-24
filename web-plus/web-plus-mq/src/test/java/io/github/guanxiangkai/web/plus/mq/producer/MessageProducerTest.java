package io.github.guanxiangkai.web.plus.mq.producer;

import io.github.guanxiangkai.web.plus.core.constant.WebPlusConstants;
import io.github.guanxiangkai.web.plus.core.context.RequestContext;
import io.github.guanxiangkai.web.plus.core.context.RequestContextHolder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageProducerTest {

    @Test
    void attachesCurrentTraceIdToMessageHeader() {
        StreamBridge streamBridge = mock(StreamBridge.class);
        when(streamBridge.send(eq("orders"), any(Message.class))).thenReturn(true);
        MessageProducer producer = new MessageProducer(streamBridge);
        RequestContextHolder.set(new RequestContext(
                "trace-mq", "/orders", "POST", null, null, System.currentTimeMillis()));

        try {
            producer.send("orders", "payload");
        } finally {
            RequestContextHolder.clear();
        }

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge).send(eq("orders"), messageCaptor.capture());
        assertThat(messageCaptor.getValue().getHeaders().get(WebPlusConstants.TRACE_ID_HEADER))
                .isEqualTo("trace-mq");
    }

    @Test
    void asyncMethodDoesNotDispatchAgainToCommonPool() {
        StreamBridge streamBridge = mock(StreamBridge.class);
        when(streamBridge.send(eq("orders"), any(Message.class))).thenReturn(true);
        MessageProducer producer = new MessageProducer(streamBridge);

        assertThat(producer.sendAsync("orders", "payload")).isCompleted();
        verify(streamBridge).send(eq("orders"), any(Message.class));
    }
}
