package io.github.guanxiangkai.web.plus.web.filter;

import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoEnvelope;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoEndpointRegistry;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoEndpointRule;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoPublicConfig;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoRuntimePolicy;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoService;
import io.github.guanxiangkai.web.plus.web.properties.ApiCryptoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.util.unit.DataSize;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiCryptoWebFilterTest {

    private static final Scheduler TEST_CRYPTO_SCHEDULER = Schedulers.boundedElastic();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDecryptEncryptedQueryParams() {
        ApiCryptoService service = createService(true, false);
        ApiCryptoWebFilter filter = createFilter(service, true, false);
        String queryValue = service.serializeEnvelopeToBase64Url(service.encryptRequestValue(Map.of(
                "keyword", "应收账款",
                "page", 2,
                "tags", List.of("overdue", "vip")
        )));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/search")
                        .queryParam(ApiCryptoService.CRYPTO_QUERY_PARAM, queryValue)
                        .build()
        );
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        WebFilterChain chain = filteredExchange -> {
            captured.set(filteredExchange);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        var queryParams = captured.get().getRequest().getQueryParams();
        assertThat(queryParams).doesNotContainKey(ApiCryptoService.CRYPTO_QUERY_PARAM);
        assertThat(queryParams.getFirst("keyword")).isEqualTo("应收账款");
        assertThat(queryParams.getFirst("page")).isEqualTo("2");
        assertThat(queryParams.get("tags")).containsExactly("overdue", "vip");
    }

    @Test
    void shouldRejectPlainQueryParamsWhenRequestCryptoEnabled() {
        ApiCryptoService service = createService(true, false);
        ApiCryptoWebFilter filter = createFilter(service, true, false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/search")
                        .queryParam("keyword", "应收账款")
                        .build()
        );
        WebFilterChain chain = filteredExchange -> Mono.empty();

        assertThatThrownBy(() -> filter.filter(exchange, chain).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("接口请求查询参数未加密");
    }

    @Test
    void shouldDecryptEncryptedJsonBody() throws Exception {
        ApiCryptoService service = createService(true, false);
        ApiCryptoWebFilter filter = createFilter(service, true, false);
        String encryptedBody = objectMapper.writeValueAsString(service.encryptRequestValue(Map.of("name", "张三")));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiCryptoService.CRYPTO_HEADER, ApiCryptoProperties.Strategy.SM4_CBC_SM3_V1.name())
                        .body(encryptedBody)
        );
        AtomicReference<String> capturedBody = new AtomicReference<>();
        WebFilterChain chain = filteredExchange -> DataBufferUtils.join(filteredExchange.getRequest().getBody())
                .doOnNext(buffer -> capturedBody.set(readAndRelease(buffer)))
                .then();

        filter.filter(exchange, chain).block();

        JsonNode decrypted = objectMapper.readTree(capturedBody.get());
        assertThat(decrypted.get("name").asString()).isEqualTo("张三");
    }

    @Test
    void shouldRejectPlainJsonBodyWhenRequestCryptoEnabled() {
        ApiCryptoService service = createService(true, false);
        ApiCryptoWebFilter filter = createFilter(service, true, false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"张三\"}")
        );
        WebFilterChain chain = filteredExchange -> Mono.empty();

        assertThatThrownBy(() -> filter.filter(exchange, chain).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("接口加密请求体格式不合法");
    }

    @Test
    void shouldRejectEncryptedRequestBodyOverConfiguredLimit() throws Exception {
        ApiCryptoService service = createService(true, false);
        ApiCryptoRuntimePolicy policy = policy(32, 1_024);
        ApiCryptoWebFilter filter = createFilter(service, true, false, policy, TEST_CRYPTO_SCHEDULER);
        String encryptedBody = objectMapper.writeValueAsString(service.encryptRequestValue(Map.of(
                "description", "超过限制的加密请求体"
        )));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(encryptedBody)
        );

        assertThatThrownBy(() -> filter.filter(exchange, ignored -> Mono.empty()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("接口加密请求体超过允许上限");
    }

    @Test
    void shouldEncryptJsonResponseDataOnly() throws Exception {
        ApiCryptoService service = createService(false, true);
        ApiCryptoWebFilter filter = createFilter(service, false, true);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/detail").build());
        WebFilterChain chain = filteredExchange -> {
            filteredExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] body = """
                    {"code":200,"data":{"name":"张三"}}
                    """.getBytes(StandardCharsets.UTF_8);
            return filteredExchange.getResponse().writeWith(Mono.just(
                    filteredExchange.getResponse().bufferFactory().wrap(body)));
        };

        filter.filter(exchange, chain).block();

        String body = exchange.getResponse().getBodyAsString().block();
        JsonNode response = objectMapper.readTree(body);
        ApiCryptoEnvelope envelope = objectMapper.treeToValue(response.get("data"), ApiCryptoEnvelope.class);
        JsonNode decryptedData = objectMapper.readTree(service.decryptResponseEnvelopeToJson(envelope));
        assertThat(exchange.getResponse().getHeaders().getFirst(ApiCryptoService.CRYPTO_HEADER))
                .isEqualTo(ApiCryptoProperties.Strategy.SM4_CBC_SM3_V1.name());
        assertThat(exchange.getResponse().getHeaders().getFirst(ApiCryptoService.CRYPTO_KEY_ID_HEADER))
                .isEqualTo("response-default");
        assertThat(response.get("code").asInt()).isEqualTo(200);
        assertThat(decryptedData.get("name").asString()).isEqualTo("张三");
    }

    @Test
    void shouldSkipJsonResponseWhenDataIsNull() {
        ApiCryptoService service = createService(false, true);
        ApiCryptoWebFilter filter = createFilter(service, false, true);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.delete("/api/delete/1").build());
        WebFilterChain chain = filteredExchange -> {
            filteredExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] body = """
                    {"code":200,"message":"操作成功","data":null}
                    """.getBytes(StandardCharsets.UTF_8);
            return filteredExchange.getResponse().writeWith(Mono.just(
                    filteredExchange.getResponse().bufferFactory().wrap(body)));
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(ApiCryptoService.CRYPTO_HEADER)).isNull();
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":200");
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"data\":null");
    }

    @Test
    void shouldRejectJsonResponseOverConfiguredLimit() {
        ApiCryptoService service = createService(false, true);
        ApiCryptoRuntimePolicy policy = policy(1_024, 32);
        ApiCryptoWebFilter filter = createFilter(service, false, true, policy, TEST_CRYPTO_SCHEDULER);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/detail").build());
        WebFilterChain chain = filteredExchange -> {
            byte[] body = "{\"code\":200,\"data\":{\"description\":\"超过响应聚合上限\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            filteredExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            filteredExchange.getResponse().getHeaders().setContentLength(body.length);
            return filteredExchange.getResponse().writeWith(Mono.just(
                    filteredExchange.getResponse().bufferFactory().wrap(body)));
        };

        assertThatThrownBy(() -> filter.filter(exchange, chain).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("接口响应超过加密聚合上限");
    }

    @Test
    void shouldKeepStreamingJsonUnaggregatedAndUnencrypted() {
        ApiCryptoService service = createService(false, true);
        ApiCryptoWebFilter filter = createFilter(service, false, true);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/stream").build());
        WebFilterChain chain = filteredExchange -> {
            filteredExchange.getResponse().getHeaders()
                    .setContentType(MediaType.parseMediaType("application/x-ndjson"));
            byte[] body = "{\"event\":1}\n{\"event\":2}\n".getBytes(StandardCharsets.UTF_8);
            return filteredExchange.getResponse().writeWith(Mono.just(
                    filteredExchange.getResponse().bufferFactory().wrap(body)));
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(ApiCryptoService.CRYPTO_HEADER)).isNull();
        assertThat(exchange.getResponse().getBodyAsString().block())
                .isEqualTo("{\"event\":1}\n{\"event\":2}\n");
    }

    @Test
    void shouldRunRequestDecryptionOnDedicatedScheduler() {
        Scheduler scheduler = Schedulers.newSingle("api-crypto-test");
        try {
            ApiCryptoService service = createService(true, false);
            ApiCryptoWebFilter filter = createFilter(
                    service, true, false, ApiCryptoRuntimePolicy.defaults(), scheduler);
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/search").build());
            AtomicReference<String> threadName = new AtomicReference<>();

            filter.filter(exchange, ignored -> {
                threadName.set(Thread.currentThread().getName());
                return Mono.empty();
            }).block();

            assertThat(threadName.get()).startsWith("api-crypto-test-");
        } finally {
            scheduler.dispose();
        }
    }

    @Test
    void shouldSkipPublicConfigPath() {
        ApiCryptoService service = createService(true, true);
        ApiCryptoWebFilter filter = createFilter(service, true, true);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(ApiCryptoPublicConfig.CONFIG_PATH)
                        .queryParam("plain", "allowed-for-config")
                        .build()
        );
        WebFilterChain chain = filteredExchange -> {
            filteredExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] body = """
                    {"enabled":true}
                    """.getBytes(StandardCharsets.UTF_8);
            return filteredExchange.getResponse().writeWith(Mono.just(
                    filteredExchange.getResponse().bufferFactory().wrap(body)));
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(ApiCryptoService.CRYPTO_HEADER)).isNull();
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"enabled\":true");
    }

    @Test
    void shouldSkipInternalPath() {
        ApiCryptoService service = createService(true, true);
        ApiCryptoWebFilter filter = createFilter(service, true, true);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/user/findByUsername")
                        .queryParam("username", "admin")
                        .build()
        );
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        WebFilterChain chain = filteredExchange -> {
            captured.set(filteredExchange);
            filteredExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] body = """
                    {"username":"admin"}
                    """.getBytes(StandardCharsets.UTF_8);
            return filteredExchange.getResponse().writeWith(Mono.just(
                    filteredExchange.getResponse().bufferFactory().wrap(body)));
        };

        filter.filter(exchange, chain).block();

        assertThat(captured.get().getRequest().getQueryParams().getFirst("username")).isEqualTo("admin");
        assertThat(exchange.getResponse().getHeaders().getFirst(ApiCryptoService.CRYPTO_HEADER)).isNull();
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"username\":\"admin\"");
    }

    @Test
    void shouldBypassRequestWithoutApiCryptoRule() {
        ApiCryptoService service = createService(true, true);
        ApiCryptoWebFilter filter = new ApiCryptoWebFilter(
                service,
                objectMapper,
                emptyRegistry(),
                ApiCryptoRuntimePolicy.defaults(),
                TEST_CRYPTO_SCHEDULER);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/search")
                        .queryParam("keyword", "明文")
                        .build()
        );
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        WebFilterChain chain = filteredExchange -> {
            captured.set(filteredExchange);
            filteredExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            return filteredExchange.getResponse().writeWith(Mono.just(
                    filteredExchange.getResponse().bufferFactory().wrap(body)));
        };

        filter.filter(exchange, chain).block();

        assertThat(captured.get().getRequest().getQueryParams().getFirst("keyword")).isEqualTo("明文");
        assertThat(exchange.getResponse().getHeaders().getFirst(ApiCryptoService.CRYPTO_HEADER)).isNull();
        assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("{\"ok\":true}");
    }

    private ApiCryptoWebFilter createFilter(ApiCryptoService service, boolean request, boolean response) {
        return createFilter(
                service,
                request,
                response,
                ApiCryptoRuntimePolicy.defaults(),
                TEST_CRYPTO_SCHEDULER);
    }

    private ApiCryptoWebFilter createFilter(
            ApiCryptoService service,
            boolean request,
            boolean response,
            ApiCryptoRuntimePolicy policy,
            Scheduler scheduler) {
        return new ApiCryptoWebFilter(service, objectMapper, matchingRegistry(request, response), policy, scheduler);
    }

    private ApiCryptoRuntimePolicy policy(long maxRequestBytes, long maxResponseBytes) {
        return new ApiCryptoRuntimePolicy(
                DataSize.ofBytes(maxRequestBytes),
                DataSize.ofBytes(maxResponseBytes),
                1_024,
                2,
                32);
    }

    private ApiCryptoEndpointRegistry matchingRegistry(boolean request, boolean response) {
        ApiCryptoEndpointRule rule = new ApiCryptoEndpointRule(List.of(), List.of("/**"), request, response);
        return new ApiCryptoEndpointRegistry(null) {
            @Override
            public Optional<ApiCryptoEndpointRule> find(ServerHttpRequest request) {
                return Optional.of(rule);
            }

            @Override
            public List<ApiCryptoEndpointRule> rules() {
                return List.of(rule);
            }
        };
    }

    private ApiCryptoEndpointRegistry emptyRegistry() {
        return new ApiCryptoEndpointRegistry(null) {
            @Override
            public Optional<ApiCryptoEndpointRule> find(ServerHttpRequest request) {
                return Optional.empty();
            }

            @Override
            public List<ApiCryptoEndpointRule> rules() {
                return List.of();
            }
        };
    }

    private ApiCryptoService createService(boolean requestEnabled, boolean responseEnabled) {
        ApiCryptoProperties properties = new ApiCryptoProperties();
        properties.setEnabled(true);
        properties.getRequest().setEnabled(requestEnabled);
        properties.getRequest().setKey("reference-request-secret");
        properties.getResponse().setEnabled(responseEnabled);
        properties.getResponse().setKey("reference-response-secret");
        return new ApiCryptoService(properties, objectMapper);
    }

    private static String readAndRelease(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
