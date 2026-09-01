package io.github.guanxiangkai.web.plus.web.filter;

import io.github.guanxiangkai.web.plus.web.annotation.ApiCrypto;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoEnvelope;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoEndpointRegistry;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoPublicConfig;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoRuntimePolicy;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoService;
import io.github.guanxiangkai.web.plus.web.properties.ApiCryptoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

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
    void shouldDecryptQueryBeforeResolvingParamsCondition() {
        ApiCryptoService service = createService(true, false);
        ApiCryptoWebFilter filter = createFilter(service, parameterizedRegistry(false));
        String queryValue = service.serializeEnvelopeToBase64Url(
                service.encryptRequestValue(Map.of("tenant", "tenant-a")));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/search")
                        .queryParam(ApiCryptoService.CRYPTO_QUERY_PARAM, queryValue)
                        .build()
        );
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, filteredExchange -> {
            captured.set(filteredExchange);
            return Mono.empty();
        }).block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getRequest().getQueryParams().getFirst("tenant")).isEqualTo("tenant-a");
        assertThat(captured.get().getRequest().getQueryParams())
                .doesNotContainKey(ApiCryptoService.CRYPTO_QUERY_PARAM);
    }

    @Test
    void shouldPreferEncryptedParamEndpointOverPlainFallback() {
        ApiCryptoService service = createService(true, false);
        ApiCryptoWebFilter filter = createFilter(service, parameterizedRegistryWithPlainFallback());
        String queryValue = service.serializeEnvelopeToBase64Url(
                service.encryptRequestValue(Map.of("tenant", "tenant-a")));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/search")
                        .queryParam(ApiCryptoService.CRYPTO_QUERY_PARAM, queryValue)
                        .build()
        );
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, filteredExchange -> {
            captured.set(filteredExchange);
            return Mono.empty();
        }).block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getRequest().getQueryParams().getFirst("tenant")).isEqualTo("tenant-a");
    }

    @Test
    void shouldRejectEncryptedQueryThatRoutesToPlainEndpoint() {
        ApiCryptoService service = createService(true, false);
        ApiCryptoWebFilter filter = createFilter(service, parameterizedRegistry(true));
        String queryValue = service.serializeEnvelopeToBase64Url(
                service.encryptRequestValue(Map.of("source", "dify")));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/search")
                        .queryParam(ApiCryptoService.CRYPTO_QUERY_PARAM, queryValue)
                        .build()
        );

        assertThatThrownBy(() -> filter.filter(exchange, ignored -> Mono.empty()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("接口加密查询参数匹配到未授权端点");
    }

    @Test
    void shouldKeepPlainDifyEndpointAvailable() {
        ApiCryptoService service = createService(true, true);
        ApiCryptoWebFilter filter = createFilter(service, parameterizedRegistry(true));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/search")
                        .queryParam("source", "dify")
                        .build()
        );
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, filteredExchange -> {
            captured.set(filteredExchange);
            return Mono.empty();
        }).block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getRequest().getQueryParams().getFirst("source")).isEqualTo("dify");
        assertThat(exchange.getResponse().getHeaders().getFirst(ApiCryptoService.CRYPTO_HEADER)).isNull();
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
    void shouldRejectNonJsonBodyWhenRequestCryptoEnabled() {
        ApiCryptoService service = createService(true, false);
        ApiCryptoWebFilter filter = createFilter(service, true, false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/save")
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("plain-text")
        );

        assertThatThrownBy(() -> filter.filter(exchange, ignored -> Mono.empty()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("接口加密请求体必须使用 JSON 媒体类型");
    }

    @Test
    void shouldRejectBodyWithoutContentTypeWhenRequestCryptoEnabled() {
        ApiCryptoService service = createService(true, false);
        ApiCryptoWebFilter filter = createFilter(service, true, false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/save").body("plain-text")
        );

        assertThatThrownBy(() -> filter.filter(exchange, ignored -> Mono.empty()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("接口加密请求体必须使用 JSON 媒体类型");
    }

    @Test
    void shouldAllowBodyMethodWithoutPayload() {
        ApiCryptoService service = createService(true, false);
        ApiCryptoWebFilter filter = createFilter(service, true, false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/action").build()
        );
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, filteredExchange -> {
            captured.set(filteredExchange);
            return Mono.empty();
        }).block();

        assertThat(captured.get()).isNotNull();
    }

    @Test
    void shouldRejectUnknownEnvelopeAlgorithmForBodyAndQuery() throws Exception {
        ApiCryptoService service = createService(true, false);
        ApiCryptoWebFilter filter = createFilter(service, true, false);
        ApiCryptoEnvelope unsupported = withAlgorithm(
                service.encryptRequestValue(Map.of("name", "张三")), "UNKNOWN");
        MockServerWebExchange bodyExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.writeValueAsString(unsupported))
        );
        MockServerWebExchange queryExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/search")
                        .queryParam(ApiCryptoService.CRYPTO_QUERY_PARAM,
                                service.serializeEnvelopeToBase64Url(unsupported))
                        .build()
        );

        assertThatThrownBy(() -> filter.filter(bodyExchange, ignored -> Mono.empty()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("接口加密请求校验失败");
        assertThatThrownBy(() -> filter.filter(queryExchange, ignored -> Mono.empty()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("接口加密请求校验失败");
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
    void shouldEncryptJsonResponseWhenDataIsNull() throws Exception {
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

        assertThat(exchange.getResponse().getHeaders().getFirst(ApiCryptoService.CRYPTO_HEADER))
                .isEqualTo(ApiCryptoProperties.Strategy.SM4_CBC_SM3_V1.name());
        JsonNode response = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        ApiCryptoEnvelope envelope = objectMapper.treeToValue(response.get("data"), ApiCryptoEnvelope.class);
        assertThat(objectMapper.readTree(service.decryptResponseEnvelopeToJson(envelope)).isNull()).isTrue();
        assertThat(response.get("code").asInt()).isEqualTo(200);
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
    void shouldRejectStreamingJsonWhenResponseCryptoIsRequired() {
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

        assertThatThrownBy(() -> filter.filter(exchange, chain).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("接口响应媒体类型不支持载荷加密");
    }

    @Test
    void shouldRejectPlainTextWhenResponseCryptoIsRequired() {
        ApiCryptoService service = createService(false, true);
        ApiCryptoWebFilter filter = createFilter(service, false, true);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/text").build());
        WebFilterChain chain = filteredExchange -> {
            filteredExchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
            byte[] body = "plain-text".getBytes(StandardCharsets.UTF_8);
            return filteredExchange.getResponse().writeWith(Mono.just(
                    filteredExchange.getResponse().bufferFactory().wrap(body)));
        };

        assertThatThrownBy(() -> filter.filter(exchange, chain).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("接口响应媒体类型不支持载荷加密");
    }

    @Test
    void shouldRejectEmptyPlainTextWhenResponseCryptoIsRequired() {
        ApiCryptoService service = createService(false, true);
        ApiCryptoWebFilter filter = createFilter(service, false, true);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/text").build());
        WebFilterChain chain = filteredExchange -> {
            filteredExchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
            return filteredExchange.getResponse().writeWith(Mono.empty());
        };

        assertThatThrownBy(() -> filter.filter(exchange, chain).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("接口响应媒体类型不支持载荷加密");
    }

    @Test
    void shouldRejectEmptyJsonWhenResponseCryptoIsRequired() {
        ApiCryptoService service = createService(false, true);
        ApiCryptoWebFilter filter = createFilter(service, false, true);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/empty").build());
        WebFilterChain chain = filteredExchange -> {
            filteredExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return filteredExchange.getResponse().writeWith(Mono.empty());
        };

        assertThatThrownBy(() -> filter.filter(exchange, chain).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("接口响应缺少可加密载荷");
    }

    @Test
    void shouldRejectSuccessfulResponseCompletedWithoutBody() {
        ApiCryptoService service = createService(false, true);
        ApiCryptoWebFilter filter = createFilter(service, false, true);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/empty").build());

        assertThatThrownBy(() -> filter.filter(
                        exchange, filteredExchange -> filteredExchange.getResponse().setComplete()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("接口响应缺少可加密载荷");
    }

    @Test
    void shouldAllowNoContentResponseWithoutCryptoEnvelope() {
        ApiCryptoService service = createService(false, true);
        ApiCryptoWebFilter filter = createFilter(service, false, true);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.delete("/api/delete/1").build());

        filter.filter(exchange, filteredExchange -> {
            filteredExchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return filteredExchange.getResponse().setComplete();
        }).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(exchange.getResponse().getHeaders().getFirst(ApiCryptoService.CRYPTO_HEADER)).isNull();
    }

    @Test
    void shouldAllowHeadResponseWithoutCryptoEnvelope() {
        ApiCryptoService service = createService(false, true);
        ApiCryptoWebFilter filter = createFilter(service, false, true);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.head("/api/detail").build());

        filter.filter(
                exchange, filteredExchange -> filteredExchange.getResponse().setComplete()).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(exchange.getResponse().getHeaders().getFirst(ApiCryptoService.CRYPTO_HEADER)).isNull();
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
    void shouldProcessExplicitInternalApiCryptoRule() throws Exception {
        ApiCryptoService service = createService(true, true);
        ApiCryptoWebFilter filter = createFilter(service, true, true);
        String queryValue = service.serializeEnvelopeToBase64Url(service.encryptRequestValue(Map.of("username", "admin")));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/user/findByUsername")
                        .queryParam(ApiCryptoService.CRYPTO_QUERY_PARAM, queryValue)
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
        assertThat(exchange.getResponse().getHeaders().getFirst(ApiCryptoService.CRYPTO_HEADER))
                .isEqualTo(ApiCryptoProperties.Strategy.SM4_CBC_SM3_V1.name());
        JsonNode response = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        ApiCryptoEnvelope envelope = objectMapper.treeToValue(response, ApiCryptoEnvelope.class);
        assertThat(objectMapper.readTree(service.decryptResponseEnvelopeToJson(envelope)).get("username").asString())
                .isEqualTo("admin");
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

    private ApiCryptoWebFilter createFilter(
            ApiCryptoService service,
            ApiCryptoEndpointRegistry registry) {
        return new ApiCryptoWebFilter(
                service,
                objectMapper,
                registry,
                ApiCryptoRuntimePolicy.defaults(),
                TEST_CRYPTO_SCHEDULER);
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
        String methodName;
        if (request && response) {
            methodName = "requestAndResponse";
        } else if (request) {
            methodName = "requestOnly";
        } else if (response) {
            methodName = "responseOnly";
        } else {
            methodName = "plain";
        }
        return registry(methodName);
    }

    private ApiCryptoEndpointRegistry emptyRegistry() {
        return registry("unannotated");
    }

    private ApiCryptoEndpointRegistry parameterizedRegistry(boolean includePlainDifyEndpoint) {
        try {
            RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();
            TestCryptoController controller = new TestCryptoController();
            handlerMapping.registerMapping(
                    RequestMappingInfo.paths("/api/search")
                            .methods(RequestMethod.GET)
                            .params("tenant")
                            .build(),
                    controller,
                    TestCryptoController.class.getMethod("secureTenant")
            );
            if (includePlainDifyEndpoint) {
                handlerMapping.registerMapping(
                        RequestMappingInfo.paths("/api/search")
                                .methods(RequestMethod.GET)
                                .params("source=dify")
                                .build(),
                        controller,
                        TestCryptoController.class.getMethod("plainDify")
                );
            }
            return new ApiCryptoEndpointRegistry(provider(handlerMapping));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("测试参数化端点不存在", e);
        }
    }

    private ApiCryptoEndpointRegistry parameterizedRegistryWithPlainFallback() {
        try {
            RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();
            TestCryptoController controller = new TestCryptoController();
            handlerMapping.registerMapping(
                    RequestMappingInfo.paths("/api/search")
                            .methods(RequestMethod.GET)
                            .params("tenant")
                            .build(),
                    controller,
                    TestCryptoController.class.getMethod("secureTenant")
            );
            handlerMapping.registerMapping(
                    RequestMappingInfo.paths("/api/search")
                            .methods(RequestMethod.GET)
                            .build(),
                    controller,
                    TestCryptoController.class.getMethod("plainDify")
            );
            return new ApiCryptoEndpointRegistry(provider(handlerMapping));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("测试参数化端点不存在", e);
        }
    }

    private ApiCryptoEndpointRegistry registry(String methodName) {
        try {
            RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();
            Method method = TestCryptoController.class.getMethod(methodName);
            handlerMapping.registerMapping(
                    RequestMappingInfo.paths("/**").build(),
                    new TestCryptoController(),
                    method
            );
            return new ApiCryptoEndpointRegistry(provider(handlerMapping));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("测试加密端点不存在", e);
        }
    }

    private ObjectProvider<RequestMappingHandlerMapping> provider(RequestMappingHandlerMapping handlerMapping) {
        return new ObjectProvider<>() {
            @Override
            public RequestMappingHandlerMapping getObject() {
                return handlerMapping;
            }

            @Override
            public Stream<RequestMappingHandlerMapping> stream() {
                return Stream.of(handlerMapping);
            }

            @Override
            public Stream<RequestMappingHandlerMapping> orderedStream() {
                return Stream.of(handlerMapping);
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

    private ApiCryptoEnvelope withAlgorithm(ApiCryptoEnvelope envelope, String algorithm) {
        return new ApiCryptoEnvelope(
                envelope.encrypted(),
                envelope.version(),
                algorithm,
                envelope.keyId(),
                envelope.iv(),
                envelope.salt(),
                envelope.data(),
                envelope.tag());
    }

    private static String readAndRelease(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static class TestCryptoController {

        @ApiCrypto
        public void requestAndResponse() {
        }

        @ApiCrypto(response = false)
        public void requestOnly() {
        }

        @ApiCrypto(request = false)
        public void responseOnly() {
        }

        @ApiCrypto(request = false, response = false)
        public void plain() {
        }

        public void unannotated() {
        }

        @ApiCrypto(response = false)
        public void secureTenant() {
        }

        public void plainDify() {
        }
    }
}
