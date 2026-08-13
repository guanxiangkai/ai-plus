package io.github.guanxiangkai.jpa.plus.audit.event;

import io.github.guanxiangkai.jpa.plus.audit.spi.AuditEventErrorHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AsyncAuditEventPublisherTest {

    @Test
    void shouldRejectNonPositiveConcurrencyLimit() {
        AuditEventPublisher delegate = mock(AuditEventPublisher.class);
        AuditEventErrorHandler errorHandler = mock(AuditEventErrorHandler.class);

        assertThatThrownBy(() -> new AsyncAuditEventPublisher(delegate, errorHandler, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("异步审计最大并发数必须大于 0");
        assertThatThrownBy(() -> new AsyncAuditEventPublisher(delegate, errorHandler, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
