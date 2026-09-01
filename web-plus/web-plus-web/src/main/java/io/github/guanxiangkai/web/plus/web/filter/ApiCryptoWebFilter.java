package io.github.guanxiangkai.web.plus.web.filter;

import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoEnvelope;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoEndpointRegistry;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoEndpointRule;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoException;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoPublicConfig;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoRuntimePolicy;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoService;
import org.reactivestreams.Publisher;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

/**
 * API 入参解密与出参加密过滤器。
 *
 * <p>处理 JSON 请求体、加密查询参数和 JSON 响应。加密查询先按不含参数的完整路由边界
 * 筛选显式候选，解密后再按 Spring 完整条件复核最终端点，禁止借解密改变到未标注路由。
 * 显式要求请求解密的端点如收到非 JSON 请求体会直接拒绝；文件、SSE、表单和其他流式端点
 * 不应标注本能力。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class ApiCryptoWebFilter implements WebFilter, Ordered {

    private static final List<HttpMethod> BODY_METHODS = List.of(
            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.PATCH,
            HttpMethod.DELETE
    );

    private final ApiCryptoService apiCryptoService;
    private final ObjectMapper objectMapper;
    private final ApiCryptoEndpointRegistry apiCryptoEndpointRegistry;
    private final ApiCryptoRuntimePolicy runtimePolicy;
    private final Scheduler cryptoScheduler;

    public ApiCryptoWebFilter(
            ApiCryptoService apiCryptoService,
            ObjectMapper objectMapper,
            ApiCryptoEndpointRegistry apiCryptoEndpointRegistry,
            ApiCryptoRuntimePolicy runtimePolicy,
            Scheduler cryptoScheduler) {
        this.apiCryptoService = apiCryptoService;
        this.objectMapper = objectMapper;
        this.apiCryptoEndpointRegistry = apiCryptoEndpointRegistry;
        this.runtimePolicy = runtimePolicy;
        this.cryptoScheduler = cryptoScheduler;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isPublicConfigPath(exchange)) {
            return chain.filter(exchange);
        }
        if (apiCryptoService.requestEnabled() && hasEncryptedQuery(exchange.getRequest())) {
            return filterEncryptedQueryCandidate(exchange, chain);
        }
        var exactMatch = apiCryptoEndpointRegistry.find(exchange);
        if (exactMatch.isPresent()) {
            var rule = exactMatch.get().rule();
            if (rule.isEmpty()) {
                return chain.filter(exchange);
            }
            return prepareExactMatch(exchange, rule.get())
                    .flatMap(resolved -> filterMatched(resolved, chain));
        }
        return chain.filter(exchange);
    }

    private Mono<Void> filterEncryptedQueryCandidate(ServerWebExchange exchange, WebFilterChain chain) {
        if (!apiCryptoService.requestEnabled() || !hasEncryptedQuery(exchange.getRequest())) {
            return chain.filter(exchange);
        }
        var candidates = apiCryptoEndpointRegistry.findEncryptedQueryCandidates(exchange);
        if (candidates.isEmpty()) {
            return chain.filter(exchange);
        }
        return decryptQuery(exchange)
                .map(decryptedExchange -> resolveCandidate(decryptedExchange, candidates.get()))
                .flatMap(resolved -> filterMatched(resolved, chain));
    }

    private boolean isPublicConfigPath(ServerWebExchange exchange) {
        return ApiCryptoPublicConfig.CONFIG_PATH.equals(exchange.getRequest().getPath().pathWithinApplication().value());
    }

    private Mono<ResolvedEndpoint> prepareExactMatch(
            ServerWebExchange exchange,
            ApiCryptoEndpointRule rule) {
        if (!rule.request() || !apiCryptoService.requestEnabled()) {
            return Mono.just(new ResolvedEndpoint(exchange, rule));
        }
        return decryptQuery(exchange)
                .map(queryExchange -> new ResolvedEndpoint(queryExchange, rule));
    }

    private Mono<ServerWebExchange> decryptQuery(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> decryptQueryParams(exchange))
                .subscribeOn(cryptoScheduler)
                .onErrorMap(ApiCryptoException.class,
                        cause -> badRequest("接口加密请求校验失败", cause));
    }

    private ResolvedEndpoint resolveCandidate(
            ServerWebExchange decryptedExchange,
            ApiCryptoEndpointRegistry.EncryptedQueryCandidates candidates) {
        ApiCryptoEndpointRegistry.EndpointMatch finalMatch = apiCryptoEndpointRegistry.find(decryptedExchange)
                .orElseThrow(() -> badRequest("接口加密查询参数无法匹配目标端点", null));
        if (!candidates.accepts(finalMatch)) {
            throw badRequest("接口加密查询参数匹配到未授权端点", null);
        }
        ApiCryptoEndpointRule finalRule = finalMatch.rule()
                .orElseThrow(() -> badRequest("接口加密查询参数匹配到未加密端点", null));
        return new ResolvedEndpoint(decryptedExchange, finalRule);
    }

    private Mono<Void> filterMatched(ResolvedEndpoint resolved, WebFilterChain chain) {
        ApiCryptoEndpointRule rule = resolved.rule();
        ServerWebExchange exchange = resolved.exchange();
        ServerWebExchange responseExchange = rule.response() && apiCryptoService.responseEnabled()
                ? exchange.mutate().response(new EncryptingResponse(
                        exchange.getResponse(), exchange.getRequest().getMethod())).build()
                : exchange;
        if (!rule.request()
                || !apiCryptoService.requestEnabled()
                || !hasDecryptableBody(responseExchange.getRequest())) {
            return chain.filter(responseExchange);
        }
        return decryptBody(responseExchange)
                .onErrorMap(ApiCryptoException.class,
                        cause -> badRequest("接口加密请求校验失败", cause))
                .flatMap(chain::filter);
    }

    private ServerWebExchange decryptQueryParams(ServerWebExchange exchange) {
        MultiValueMap<String, String> originalQueryParams = exchange.getRequest().getQueryParams();
        String rawEnvelope = originalQueryParams.getFirst(ApiCryptoService.CRYPTO_QUERY_PARAM);
        if (rawEnvelope == null) {
            if (!originalQueryParams.isEmpty()) {
                throw badRequest("接口请求查询参数未加密", null);
            }
            return exchange;
        }
        if (originalQueryParams.size() > 1) {
            throw badRequest("接口加密查询参数不能混用明文参数", null);
        }
        if (rawEnvelope.length() > runtimePolicy.maxQueryEnvelopeLength()) {
            throw new ResponseStatusException(HttpStatus.URI_TOO_LONG, "接口加密查询参数超过允许上限");
        }
        ApiCryptoEnvelope envelope = apiCryptoService.readEnvelope(rawEnvelope)
                .orElseThrow(() -> badRequest("接口加密查询参数格式不合法", null));
        JsonNode queryNode = apiCryptoService.decryptRequestEnvelopeToTree(envelope);
        if (!queryNode.isObject()) {
            throw badRequest("接口加密查询参数明文必须是 JSON 对象", null);
        }

        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        originalQueryParams.forEach((name, values) -> {
            if (!ApiCryptoService.CRYPTO_QUERY_PARAM.equals(name)) {
                queryParams.addAll(name, values);
            }
        });
        queryNode.properties().forEach(entry -> appendQueryParam(queryParams, entry.getKey(), entry.getValue()));

        URI uri = UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                .replaceQueryParams(queryParams)
                .build()
                .toUri();
        ServerHttpRequest request = exchange.getRequest().mutate().uri(uri).build();
        return exchange.mutate().request(request).build();
    }

    private boolean hasEncryptedQuery(ServerHttpRequest request) {
        return request.getQueryParams().containsKey(ApiCryptoService.CRYPTO_QUERY_PARAM);
    }

    private void appendQueryParam(MultiValueMap<String, String> queryParams, String name, JsonNode value) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isArray()) {
            value.values().forEach(item -> queryParams.add(name, toQueryValue(item)));
            return;
        }
        queryParams.add(name, toQueryValue(value));
    }

    private String toQueryValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isValueNode()) {
            return value.asString();
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw badRequest("接口加密查询参数序列化失败", e);
        }
    }

    private Mono<ServerWebExchange> decryptBody(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        long contentLength = request.getHeaders().getContentLength();
        if (contentLength > runtimePolicy.maxRequestBodyBytes()) {
            return Mono.error(payloadTooLarge("接口加密请求体超过允许上限", null));
        }
        return DataBufferUtils.join(request.getBody(), runtimePolicy.maxRequestBodyBytes())
                .switchIfEmpty(Mono.fromSupplier(() -> exchange.getResponse().bufferFactory().wrap(new byte[0])))
                .publishOn(cryptoScheduler)
                .flatMap(buffer -> {
                    byte[] bytes = readAndRelease(buffer);
                    if (bytes.length == 0) {
                        return Mono.just(exchange);
                    }
                    MediaType contentType = request.getHeaders().getContentType();
                    if (!isJson(contentType)) {
                        return Mono.error(unsupportedMediaType(
                                "接口加密请求体必须使用 JSON 媒体类型", null));
                    }
                    String rawBody = new String(bytes, StandardCharsets.UTF_8);
                    var envelope = apiCryptoService.readEnvelope(rawBody);
                    if (envelope.isEmpty()) {
                        return Mono.error(badRequest("接口加密请求体格式不合法", null));
                    }
                    byte[] decryptedBody = apiCryptoService.decryptRequestEnvelopeToJson(envelope.get())
                            .getBytes(StandardCharsets.UTF_8);
                    ServerHttpRequest decryptedRequest = new DecryptedBodyRequest(request, decryptedBody,
                            exchange.getResponse().bufferFactory());
                    return Mono.just(exchange.mutate().request(decryptedRequest).build());
                })
                .onErrorMap(DataBufferLimitException.class,
                        cause -> payloadTooLarge("接口加密请求体超过允许上限", cause));
    }

    private boolean hasDecryptableBody(ServerHttpRequest request) {
        return BODY_METHODS.contains(request.getMethod()) || hasCryptoHeader(request);
    }

    private boolean hasCryptoHeader(ServerHttpRequest request) {
        return request.getHeaders().getFirst(ApiCryptoService.CRYPTO_HEADER) != null
                || request.getHeaders().getFirst(ApiCryptoService.CRYPTO_KEY_ID_HEADER) != null;
    }

    private boolean hasNoResponseBody(ServerHttpResponse response, HttpMethod requestMethod) {
        if (HttpMethod.HEAD.equals(requestMethod)) {
            return true;
        }
        var status = response.getStatusCode();
        return status != null
                && (status.is1xxInformational()
                || status.value() == HttpStatus.NO_CONTENT.value()
                || status.value() == HttpStatus.RESET_CONTENT.value()
                || status.value() == HttpStatus.NOT_MODIFIED.value());
    }

    private boolean supportsResponseEncryption(MediaType mediaType) {
        return isJson(mediaType) && !isStreamingJson(mediaType);
    }

    private boolean isStreamingJson(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        String subtype = mediaType.getSubtype();
        return "x-ndjson".equalsIgnoreCase(subtype) || "stream+json".equalsIgnoreCase(subtype);
    }

    private boolean isJson(MediaType mediaType) {
        return mediaType != null
                && (MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)
                || mediaType.getSubtype().endsWith("+json"));
    }

    private byte[] readAndRelease(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return bytes;
    }

    private ResponseStatusException badRequest(String message, Throwable cause) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message, cause);
    }

    private ResponseStatusException payloadTooLarge(String message, Throwable cause) {
        return new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, message, cause);
    }

    private ResponseStatusException unsupportedMediaType(String message, Throwable cause) {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message, cause);
    }

    private ResponseStatusException responseTooLarge(Throwable cause) {
        return new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "接口响应超过加密聚合上限",
                cause);
    }

    private ResponseStatusException unsupportedEncryptedResponse(Throwable cause) {
        return new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "接口响应媒体类型不支持载荷加密",
                cause);
    }

    private ResponseStatusException emptyEncryptedResponse(Throwable cause) {
        return new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "接口响应缺少可加密载荷",
                cause);
    }

    private final class DecryptedBodyRequest extends ServerHttpRequestDecorator {

        private final byte[] body;
        private final DataBufferFactory bufferFactory;

        private DecryptedBodyRequest(ServerHttpRequest delegate, byte[] body, DataBufferFactory bufferFactory) {
            super(delegate);
            this.body = body;
            this.bufferFactory = bufferFactory;
        }

        @Override
        public HttpHeaders getHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.putAll(super.getHeaders());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentLength(body.length);
            return headers;
        }

        @Override
        public Flux<DataBuffer> getBody() {
            return Flux.defer(() -> Flux.just(bufferFactory.wrap(body)));
        }
    }

    private final class EncryptingResponse extends ServerHttpResponseDecorator {

        private final HttpMethod requestMethod;

        private EncryptingResponse(ServerHttpResponse delegate, HttpMethod requestMethod) {
            super(delegate);
            this.requestMethod = requestMethod;
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            if (hasNoResponseBody(this, requestMethod)) {
                return super.writeWith(body);
            }
            if (isCommitted()) {
                return Mono.error(unsupportedEncryptedResponse(null));
            }
            MediaType contentType = getHeaders().getContentType();
            if (!supportsResponseEncryption(contentType)) {
                return Mono.error(unsupportedEncryptedResponse(null));
            }
            long contentLength = getHeaders().getContentLength();
            if (contentLength > runtimePolicy.maxResponseBodyBytes()) {
                return Mono.error(responseTooLarge(null));
            }
            return DataBufferUtils.join(Flux.from(body), runtimePolicy.maxResponseBodyBytes())
                    .switchIfEmpty(Mono.error(emptyEncryptedResponse(null)))
                    .publishOn(cryptoScheduler)
                    .flatMap(buffer -> {
                        byte[] originalBody = readAndRelease(buffer);
                        if (originalBody.length == 0) {
                            return Mono.error(emptyEncryptedResponse(null));
                        }
                        try {
                            byte[] encryptedBody = encryptResponseBody(originalBody);
                            HttpHeaders headers = getHeaders();
                            headers.setContentType(MediaType.APPLICATION_JSON);
                            headers.setContentLength(encryptedBody.length);
                            headers.set(ApiCryptoService.CRYPTO_HEADER, apiCryptoService.responseStrategyHeader());
                            headers.set(ApiCryptoService.CRYPTO_KEY_ID_HEADER, apiCryptoService.responseKeyId());
                            return super.writeWith(Mono.just(bufferFactory().wrap(encryptedBody)));
                        } catch (ApiCryptoException e) {
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.INTERNAL_SERVER_ERROR, "接口响应加密失败", e));
                        }
                    })
                    .onErrorMap(DataBufferLimitException.class, ApiCryptoWebFilter.this::responseTooLarge);
        }

        @Override
        public Mono<Void> setComplete() {
            if (hasNoResponseBody(this, requestMethod)) {
                return super.setComplete();
            }
            return Mono.error(emptyEncryptedResponse(null));
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            return writeWith(Flux.from(body).flatMapSequential(Function.identity()));
        }
    }

    private byte[] encryptResponseBody(byte[] originalBody) {
        String rawJson = new String(originalBody, StandardCharsets.UTF_8);
        byte[] encryptedDataField = encryptDataField(rawJson);
        if (encryptedDataField != null) {
            return encryptedDataField;
        }
        ApiCryptoEnvelope envelope = apiCryptoService.encryptResponseJson(rawJson);
        return apiCryptoService.serializeEnvelope(envelope);
    }

    private byte[] encryptDataField(String rawJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (JacksonException e) {
            return null;
        }
        if (!root.isObject() || !root.has("data")) {
            return null;
        }
        JsonNode data = root.get("data");
        try {
            ApiCryptoEnvelope envelope = apiCryptoService.encryptResponseJson(objectMapper.writeValueAsString(data));
            ObjectNode encryptedRoot = root.asObject().deepCopy();
            encryptedRoot.set("data", objectMapper.valueToTree(envelope));
            return objectMapper.writeValueAsBytes(encryptedRoot);
        } catch (JacksonException e) {
            throw new ApiCryptoException("接口响应 data 加密失败", e);
        }
    }

    private record ResolvedEndpoint(
            ServerWebExchange exchange,
            ApiCryptoEndpointRule rule
    ) {
    }
}
