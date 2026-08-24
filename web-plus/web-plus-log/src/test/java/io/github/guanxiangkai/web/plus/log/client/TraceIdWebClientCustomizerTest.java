package io.github.guanxiangkai.web.plus.log.client;

import io.github.guanxiangkai.web.plus.core.constant.WebPlusConstants;
import io.github.guanxiangkai.web.plus.core.context.RequestContext;
import io.github.guanxiangkai.web.plus.core.context.RequestContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdWebClientCustomizerTest {

    @Test
    void propagatesTraceIdFromReactorContext() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        });
        new TraceIdWebClientCustomizer(WebPlusConstants.TRACE_ID_HEADER).customize(builder);
        RequestContext requestContext = new RequestContext(
                "trace-http", "/source", "GET", null, null, System.currentTimeMillis());

        builder.build().get().uri("http://downstream.test/resource")
                .exchangeToMono(response -> Mono.empty())
                .contextWrite(context -> context.put(RequestContextHolder.REACTOR_CONTEXT_KEY, requestContext))
                .block();

        assertThat(captured.get().headers().getFirst(WebPlusConstants.TRACE_ID_HEADER))
                .isEqualTo("trace-http");
    }

    @Test
    void preservesExplicitTraceIdHeader() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        });
        new TraceIdWebClientCustomizer(WebPlusConstants.TRACE_ID_HEADER).customize(builder);
        RequestContext requestContext = new RequestContext(
                "trace-context", "/source", "GET", null, null, System.currentTimeMillis());

        builder.build().get().uri("http://downstream.test/resource")
                .header(WebPlusConstants.TRACE_ID_HEADER, "trace-explicit")
                .exchangeToMono(response -> Mono.empty())
                .contextWrite(context -> context.put(RequestContextHolder.REACTOR_CONTEXT_KEY, requestContext))
                .block();

        assertThat(captured.get().headers().getFirst(WebPlusConstants.TRACE_ID_HEADER))
                .isEqualTo("trace-explicit");
    }
}
