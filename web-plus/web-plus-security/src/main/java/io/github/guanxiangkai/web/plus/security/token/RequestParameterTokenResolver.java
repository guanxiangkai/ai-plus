package io.github.guanxiangkai.web.plus.security.token;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE;

/**
 * 从 URL 查询参数或 JSON 请求体解析请求参数令牌。
 *
 * <p>查询参数 {@code token} 优先于请求体顶层 {@code token} 字段。解析到的查询参数会从下游 URL
 * 移除；读取 JSON 请求体后会重放请求体，若令牌来自请求体，重放给下游的 JSON 也不再包含该字段，
 * 避免令牌进入业务绑定、日志或审计载荷。</p>
 *
 * <p>该类型不注册过滤器或自动配置，调用方应在自己的认证链中显式决定何时解析及如何使用结果。</p>
 */
public final class RequestParameterTokenResolver {

    /** 默认最多缓存 1 MiB JSON 请求体。 */
    public static final int DEFAULT_MAX_CACHED_BODY_BYTES = 1024 * 1024;

    /** 配置允许的请求体缓存上限，避免调用方误配成无界聚合。 */
    public static final int MAX_CACHED_BODY_BYTES = 10 * 1024 * 1024;

    /** 默认令牌最大长度，超出即拒绝请求。 */
    public static final int DEFAULT_MAX_TOKEN_LENGTH = 16 * 1024;

    /** 认证令牌使用的固定请求参数名称。 */
    public static final String TOKEN_PARAMETER = "token";
    private static final byte[] EMPTY_BODY = new byte[0];

    private final ObjectMapper objectMapper;
    private final int maxCachedBodyBytes;
    private final int maxTokenLength;

    /**
     * 使用 1 MiB 请求体缓存上限和默认令牌长度上限创建解析器。
     *
     * @param objectMapper JSON 编解码器
     */
    public RequestParameterTokenResolver(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_MAX_CACHED_BODY_BYTES, DEFAULT_MAX_TOKEN_LENGTH);
    }

    /**
     * 使用指定请求体缓存上限和默认令牌长度上限创建解析器。
     *
     * @param objectMapper JSON 编解码器
     * @param maxCachedBodyBytes 可聚合并重放的 JSON 请求体最大字节数
     */
    public RequestParameterTokenResolver(ObjectMapper objectMapper, int maxCachedBodyBytes) {
        this(objectMapper, maxCachedBodyBytes, DEFAULT_MAX_TOKEN_LENGTH);
    }

    /**
     * 使用指定的请求体缓存和令牌长度上限创建解析器。
     *
     * @param objectMapper JSON 编解码器
     * @param maxCachedBodyBytes 可聚合并重放的 JSON 请求体最大字节数
     * @param maxTokenLength 允许的令牌最大字符数
     */
    public RequestParameterTokenResolver(ObjectMapper objectMapper, int maxCachedBodyBytes, int maxTokenLength) {
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper");
        if (maxCachedBodyBytes <= 0 || maxCachedBodyBytes > MAX_CACHED_BODY_BYTES) {
            throw new IllegalArgumentException("maxCachedBodyBytes 必须介于 1 字节与 10 MiB 之间");
        }
        if (maxTokenLength <= 0) {
            throw new IllegalArgumentException("maxTokenLength 必须大于零");
        }
        this.maxCachedBodyBytes = maxCachedBodyBytes;
        this.maxTokenLength = maxTokenLength;
    }

    /**
     * 解析请求中的令牌，并在读取 JSON 请求体后提供可继续使用的交换对象。
     *
     * @param exchange 当前 WebFlux 请求交换对象
     * @return 包含令牌、来源和可下传交换对象的异步结果
     */
    public Mono<RequestParameterTokenResolution> resolve(ServerWebExchange exchange) {
        return Mono.defer(() -> {
            Optional<String> queryToken = resolveQueryToken(exchange.getRequest().getQueryParams());
            if (queryToken.isPresent()) {
                return Mono.just(new RequestParameterTokenResolution(
                        queryToken, RequestParameterTokenSource.QUERY, withoutTokenQuery(exchange)));
            }
            if (!isCacheableJson(exchange.getRequest().getHeaders().getContentType())) {
                return Mono.just(RequestParameterTokenResolution.absent(exchange));
            }
            long contentLength = exchange.getRequest().getHeaders().getContentLength();
            if (contentLength > maxCachedBodyBytes) {
                return Mono.error(bodyTooLarge());
            }
            return DataBufferUtils.join(exchange.getRequest().getBody(), maxCachedBodyBytes)
                    .defaultIfEmpty(DefaultDataBufferFactory.sharedInstance.wrap(EMPTY_BODY))
                    .onErrorMap(DataBufferLimitException.class, ignored -> bodyTooLarge())
                    .publishOn(Schedulers.boundedElastic())
                    .map(this::readAndRelease)
                    .map(body -> resolveBodyToken(exchange, body));
        });
    }

    private Optional<String> resolveQueryToken(MultiValueMap<String, String> queryParameters) {
        if (!queryParameters.containsKey(TOKEN_PARAMETER)) {
            return Optional.empty();
        }
        List<String> values = queryParameters.get(TOKEN_PARAMETER);
        if (values == null || values.size() != 1) {
            throw invalidToken();
        }
        return Optional.of(validateToken(values.getFirst()));
    }

    private ServerWebExchange withoutTokenQuery(ServerWebExchange exchange) {
        var queryParameters = new org.springframework.util.LinkedMultiValueMap<>(
                exchange.getRequest().getQueryParams());
        queryParameters.remove(TOKEN_PARAMETER);
        var uri = UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                .replaceQueryParams(queryParameters)
                .build()
                .encode()
                .toUri();
        return exchange.mutate().request(exchange.getRequest().mutate().uri(uri).build()).build();
    }

    private RequestParameterTokenResolution resolveBodyToken(ServerWebExchange exchange, byte[] originalBody) {
        ServerWebExchange replayedExchange = exchange.mutate()
                .request(new CachedBodyRequest(exchange.getRequest(), originalBody))
                .build();
        if (originalBody.length == 0) {
            return RequestParameterTokenResolution.absent(replayedExchange);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(originalBody);
        } catch (JacksonException ignored) {
            return RequestParameterTokenResolution.absent(replayedExchange);
        }
        if (!(root instanceof ObjectNode objectNode) || !objectNode.has(TOKEN_PARAMETER)) {
            return RequestParameterTokenResolution.absent(replayedExchange);
        }
        JsonNode tokenNode = objectNode.get(TOKEN_PARAMETER);
        if (tokenNode == null || !tokenNode.isString()) {
            throw invalidToken();
        }
        String token = validateToken(tokenNode.stringValue());
        objectNode.remove(TOKEN_PARAMETER);
        byte[] downstreamBody;
        try {
            downstreamBody = objectMapper.writeValueAsBytes(objectNode);
        } catch (JacksonException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "请求体无法重放", exception);
        }
        ServerWebExchange sanitizedExchange = exchange.mutate()
                .request(new CachedBodyRequest(exchange.getRequest(), downstreamBody))
                .build();
        return new RequestParameterTokenResolution(
                Optional.of(token), RequestParameterTokenSource.BODY, sanitizedExchange);
    }

    private String validateToken(String token) {
        if (token == null || token.isBlank() || token.length() > maxTokenLength
                || token.codePoints().anyMatch(codePoint -> Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint))) {
            throw invalidToken();
        }
        return token;
    }

    private boolean isCacheableJson(MediaType contentType) {
        if (contentType == null) {
            return false;
        }
        String subtype = contentType.getSubtype();
        boolean json = MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                || subtype.endsWith("+json");
        return json && !"x-ndjson".equalsIgnoreCase(subtype) && !"stream+json".equalsIgnoreCase(subtype);
    }

    private byte[] readAndRelease(DataBuffer buffer) {
        try {
            byte[] body = new byte[buffer.readableByteCount()];
            buffer.read(body);
            return body;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(BAD_REQUEST, "请求令牌不合法");
    }

    private ResponseStatusException bodyTooLarge() {
        return new ResponseStatusException(CONTENT_TOO_LARGE, "请求体超过令牌解析缓存上限");
    }

    private static final class CachedBodyRequest extends ServerHttpRequestDecorator {

        private final byte[] body;

        private CachedBodyRequest(ServerHttpRequest delegate, byte[] body) {
            super(delegate);
            this.body = body;
        }

        @Override
        public HttpHeaders getHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.putAll(super.getHeaders());
            headers.remove(HttpHeaders.TRANSFER_ENCODING);
            headers.setContentLength(body.length);
            return headers;
        }

        @Override
        public Flux<DataBuffer> getBody() {
            return Flux.defer(() -> Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(body)));
        }
    }
}
