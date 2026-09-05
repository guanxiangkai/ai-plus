package io.github.guanxiangkai.web.plus.security.token;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestParameterTokenResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RequestParameterTokenResolver resolver = new RequestParameterTokenResolver(objectMapper);

    @Test
    void queryTokenTakesPrecedenceAndStillRemovesBodyToken() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/submit?token=query-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"token\":\"query-token\",\"name\":\"张三\"}"));

        RequestParameterTokenResolution resolution = resolver.resolve(exchange).block();

        assertThat(resolution.token()).contains("query-token");
        assertThat(resolution.source()).isEqualTo(RequestParameterTokenSource.QUERY);
        assertThat(resolution.exchange().getRequest().getQueryParams()).doesNotContainKey("token");
        JsonNode downstreamJson = objectMapper.readTree(readBody(resolution.exchange()));
        assertThat(downstreamJson.has("token")).isFalse();
        assertThat(downstreamJson.path("name").stringValue()).isEqualTo("张三");
    }

    @Test
    void removesOnlyTokenQuerySegmentsWithoutReencodingRawUri() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(org.springframework.http.HttpMethod.GET,
                        java.net.URI.create("https://example.test/api%2Fv1?keep=%252F&%74oken=query-token&empty=&repeat=a&repeat=b")));

        RequestParameterTokenResolution resolution = resolver.resolve(exchange).block();

        assertThat(resolution.token()).contains("query-token");
        assertThat(resolution.exchange().getRequest().getURI().getRawPath()).isEqualTo("/api%2Fv1");
        assertThat(resolution.exchange().getRequest().getURI().getRawQuery())
                .isEqualTo("keep=%252F&empty=&repeat=a&repeat=b");
    }

    @Test
    void bodyTokenIsExtractedAndRemovedFromDownstreamJson() throws Exception {
        RequestParameterTokenResolution resolution = resolver.resolve(
                exchange("{\"token\":\"body-token\",\"name\":\"张三\"}"))
                .block();

        assertThat(resolution.token()).contains("body-token");
        assertThat(resolution.source()).isEqualTo(RequestParameterTokenSource.BODY);
        JsonNode downstreamJson = objectMapper.readTree(readBody(resolution.exchange()));
        assertThat(downstreamJson.has("token")).isFalse();
        assertThat(downstreamJson.path("name").stringValue()).isEqualTo("张三");
    }

    @Test
    void jsonWithoutTokenOrWithInvalidOrTrailingJsonIsReplayedWithoutToken() {
        String originalBody = "{\"name\":\"张三\",\"count\":2}";

        RequestParameterTokenResolution resolution = resolver.resolve(exchange(originalBody)).block();

        assertThat(resolution.token()).isEmpty();
        assertThat(resolution.source()).isEqualTo(RequestParameterTokenSource.NONE);
        assertThat(readBody(resolution.exchange())).isEqualTo(originalBody);

        String invalidJson = "{not-json";
        RequestParameterTokenResolution invalidResolution = resolver.resolve(exchange(invalidJson)).block();
        assertThat(invalidResolution.token()).isEmpty();
        assertThat(readBody(invalidResolution.exchange())).isEqualTo(invalidJson);

        String trailingJson = "{\"token\":\"not-authenticated\"} trailing";
        RequestParameterTokenResolution trailingResolution = resolver.resolve(exchange(trailingJson)).block();
        assertThat(trailingResolution.token()).isEmpty();
        assertThat(readBody(trailingResolution.exchange())).isEqualTo(trailingJson);
    }

    @Test
    void rejectsRepeatedAndInvalidTokensWithoutEchoingValue() {
        MockServerWebExchange repeated = MockServerWebExchange.from(
                MockServerHttpRequest.get("/submit?token=first&token=second"));
        MockServerWebExchange blank = exchange("{\"token\":\"  \"}");
        MockServerWebExchange whitespace = exchange("{\"token\":\"has space\"}");
        MockServerWebExchange nonString = exchange("{\"token\":123}");
        MockServerWebExchange duplicateBodyToken = exchange("{\"token\":\"first\",\"token\":\"second\"}");
        MockServerWebExchange controlCharacter = exchange("{\"token\":\"bad\\u0001token\"}");
        RequestParameterTokenResolver shortTokenResolver = new RequestParameterTokenResolver(objectMapper, 1024, 3);

        assertRejectedWithoutToken(() -> resolver.resolve(repeated).block(), "first");
        assertRejectedWithoutToken(() -> resolver.resolve(blank).block(), "  ");
        assertRejectedWithoutToken(() -> resolver.resolve(whitespace).block(), "has space");
        assertRejectedWithoutToken(() -> resolver.resolve(nonString).block(), "123");
        assertRejectedWithoutToken(() -> resolver.resolve(duplicateBodyToken).block(), "first");
        assertRejectedWithoutToken(() -> resolver.resolve(controlCharacter).block(), "bad");
        assertRejectedWithoutToken(() -> shortTokenResolver.resolve(exchange("{\"token\":\"long\"}")).block(), "long");
    }

    @Test
    void rejectsBodiesOverConfiguredCacheLimit() {
        RequestParameterTokenResolver limitedResolver = new RequestParameterTokenResolver(objectMapper, 8);

        assertThatThrownBy(() -> limitedResolver.resolve(exchange("{\"name\":\"too long\"}")).block())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONTENT_TOO_LARGE));
    }

    @Test
    void rejectsUnboundedCacheConfiguration() {
        assertThatThrownBy(() -> new RequestParameterTokenResolver(
                objectMapper, RequestParameterTokenResolver.MAX_CACHED_BODY_BYTES + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 MiB");
    }

    @Test
    void resolutionToStringDoesNotExposeTokenOrExchange() {
        MockServerWebExchange exchange = exchange("{\"name\":\"张三\"}");
        RequestParameterTokenResolution resolution = new RequestParameterTokenResolution(
                java.util.Optional.of("sensitive-token"), RequestParameterTokenSource.QUERY, exchange);

        assertThat(resolution).hasToString(
                "RequestParameterTokenResolution[token=<redacted>, source=QUERY, exchange=<redacted>]");
    }

    @Test
    void releasesCachedDataBufferWhenSubscriptionIsCancelledAfterPublishOn() {
        PooledDataBuffer body = mock(PooledDataBuffer.class);
        java.util.concurrent.atomic.AtomicBoolean allocated = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(body.isAllocated()).thenAnswer(ignored -> allocated.get());
        when(body.release()).thenAnswer(ignored -> allocated.compareAndSet(true, false));
        when(body.readableByteCount()).thenReturn(2);
        when(body.read(any(byte[].class))).thenAnswer(invocation -> {
            byte[] bytes = invocation.getArgument(0);
            bytes[0] = '{';
            bytes[1] = '}';
            return body;
        });
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
        RequestParameterTokenResolver cancellableResolver = new RequestParameterTokenResolver(
                objectMapper, 1024, 32, scheduler);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Flux.just(body)));

        StepVerifier.create(cancellableResolver.resolve(exchange))
                .thenCancel()
                .verify();

        verify(body).release();
    }

    private MockServerWebExchange exchange(String body) {
        return MockServerWebExchange.from(MockServerHttpRequest.post("/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));
    }

    private String readBody(org.springframework.web.server.ServerWebExchange exchange) {
        DataBuffer buffer = DataBufferUtils.join(exchange.getRequest().getBody()).block();
        if (buffer == null) {
            return "";
        }
        try {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private void assertRejectedWithoutToken(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
                                            String token) {
        assertThatThrownBy(action)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException = (ResponseStatusException) exception;
                    assertThat(responseException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(responseException.getMessage()).doesNotContain(token);
                });
    }
}
