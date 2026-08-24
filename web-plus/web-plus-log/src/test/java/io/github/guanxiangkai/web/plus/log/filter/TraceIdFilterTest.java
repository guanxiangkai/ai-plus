package io.github.guanxiangkai.web.plus.log.filter;

import io.github.guanxiangkai.web.plus.core.constant.WebPlusConstants;
import io.github.guanxiangkai.web.plus.core.context.RequestContext;
import io.github.guanxiangkai.web.plus.core.context.RequestContextHolder;
import io.github.guanxiangkai.web.plus.core.spi.TraceIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    @Test
    void propagatesIncomingTraceIdToRequestResponseAndReactorContext() {
        TraceIdFilter filter = new TraceIdFilter(() -> "generated-trace", WebPlusConstants.TRACE_ID_HEADER);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/orders")
                .header(WebPlusConstants.TRACE_ID_HEADER, "incoming-trace"));
        AtomicReference<String> requestHeader = new AtomicReference<>();
        AtomicReference<RequestContext> context = new AtomicReference<>();

        filter.filter(exchange, tracedExchange -> Mono.deferContextual(contextView -> {
            requestHeader.set(tracedExchange.getRequest().getHeaders()
                    .getFirst(WebPlusConstants.TRACE_ID_HEADER));
            context.set(contextView.get(RequestContextHolder.REACTOR_CONTEXT_KEY));
            return Mono.empty();
        })).block();

        assertThat(requestHeader).hasValue("incoming-trace");
        assertThat(context.get().traceId()).isEqualTo("incoming-trace");
        assertThat(exchange.getResponse().getHeaders().getFirst(WebPlusConstants.TRACE_ID_HEADER))
                .isEqualTo("incoming-trace");
        assertThat(exchange.getResponse().getHeaders().getValuesAsList(
                HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS)).contains(WebPlusConstants.TRACE_ID_HEADER);
    }

    @Test
    void currentStandardTraceIdOverridesUntrustedCorrelationHeader() {
        TraceIdGenerator generator = new TraceIdGenerator() {
            @Override
            public String currentTraceId() {
                return "standard-trace";
            }

            @Override
            public String generate() {
                return "generated-trace";
            }
        };
        TraceIdFilter filter = new TraceIdFilter(generator, WebPlusConstants.TRACE_ID_HEADER);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/orders")
                .header(WebPlusConstants.TRACE_ID_HEADER, "client-trace"));
        AtomicReference<String> requestHeader = new AtomicReference<>();

        filter.filter(exchange, tracedExchange -> {
            requestHeader.set(tracedExchange.getRequest().getHeaders()
                    .getFirst(WebPlusConstants.TRACE_ID_HEADER));
            return Mono.empty();
        }).block();

        assertThat(requestHeader).hasValue("standard-trace");
        assertThat(exchange.getResponse().getHeaders().getFirst(WebPlusConstants.TRACE_ID_HEADER))
                .isEqualTo("standard-trace");
    }

    @Test
    void rejectsUnsafeHeaderAndGeneratesNewTraceId() {
        TraceIdFilter filter = new TraceIdFilter(() -> "generated-trace", WebPlusConstants.TRACE_ID_HEADER);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/orders")
                .header(WebPlusConstants.TRACE_ID_HEADER, "bad trace value"));

        filter.filter(exchange, tracedExchange -> {
            assertThat(tracedExchange.getRequest().getHeaders()
                    .getFirst(WebPlusConstants.TRACE_ID_HEADER)).isEqualTo("generated-trace");
            return Mono.empty();
        }).block();
    }
}
