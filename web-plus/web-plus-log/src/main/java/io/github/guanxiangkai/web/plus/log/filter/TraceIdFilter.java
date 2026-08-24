package io.github.guanxiangkai.web.plus.log.filter;

import io.github.guanxiangkai.web.plus.core.context.RequestContext;
import io.github.guanxiangkai.web.plus.core.context.RequestContextThreadLocalAccessor;
import io.github.guanxiangkai.web.plus.core.spi.TraceIdGenerator;
import io.github.guanxiangkai.web.plus.core.trace.TraceId;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * TraceId 过滤器（WebFlux WebFilter）
 * <p>
 * 最高优先级，在请求入口：
 * <ol>
 *   <li>从 {@code X-Trace-Id} 请求头读取或生成新的 TraceId</li>
 *   <li>写入 MDC 供日志框架使用</li>
 *   <li>写入 Reactor Context，并由统一适配器同步到 RequestContextHolder 与 MDC</li>
 *   <li>在响应头中回写 TraceId</li>
 * </ol>
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@RequiredArgsConstructor
public class TraceIdFilter implements WebFilter, Ordered {

    private final TraceIdGenerator traceIdGenerator;
    private final String traceHeader;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            ServerHttpRequest request = exchange.getRequest();
            String traceId = resolveTraceId(request);
            RequestContext requestContext = new RequestContext(
                    traceId,
                    request.getURI().getPath(),
                    request.getMethod().name(),
                    null,
                    request.getHeaders().getFirst("User-Agent"),
                    System.currentTimeMillis()
            );

            ServerHttpRequest tracedRequest = request.mutate()
                    .headers(headers -> headers.set(traceHeader, traceId))
                    .build();
            ServerWebExchange tracedExchange = exchange.mutate().request(tracedRequest).build();
            tracedExchange.getResponse().getHeaders().set(traceHeader, traceId);
            tracedExchange.getResponse().getHeaders().add(
                    HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, traceHeader);

            return Mono.defer(() -> chain.filter(tracedExchange))
                    .contextWrite(context -> context.put(RequestContextThreadLocalAccessor.KEY, requestContext));
        });
    }

    private String resolveTraceId(ServerHttpRequest request) {
        String traceId = traceIdGenerator.currentTraceId();
        if (!TraceId.isValid(traceId)) {
            traceId = TraceId.fromHeader(request.getHeaders().getFirst(traceHeader));
        }
        if (!TraceId.isValid(traceId)) {
            traceId = traceIdGenerator.generate();
        }
        if (!TraceId.isValid(traceId)) {
            throw new IllegalStateException("TraceIdGenerator 返回了非法 TraceId");
        }
        return traceId;
    }
}
