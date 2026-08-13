package io.github.guanxiangkai.web.plus.log.filter;

import io.github.guanxiangkai.web.plus.core.constant.WebPlusConstants;
import io.github.guanxiangkai.web.plus.core.context.RequestContext;
import io.github.guanxiangkai.web.plus.core.context.RequestContextHolder;
import io.github.guanxiangkai.web.plus.core.spi.TraceIdGenerator;
import io.github.guanxiangkai.web.plus.log.context.RequestContextThreadLocalAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

/**
 * TraceId 过滤器（WebFlux WebFilter）
 * <p>
 * 最高优先级，在请求入口：
 * <ol>
 *   <li>从 {@code X-Trace-Id} 请求头读取或生成新的 TraceId</li>
 *   <li>写入 MDC 供日志框架使用</li>
 *   <li>写入 {@link RequestContextHolder}</li>
 *   <li>在响应头中回写 TraceId</li>
 * </ol>
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class TraceIdFilter implements WebFilter, Ordered {

    private final TraceIdGenerator traceIdGenerator;
    private final String traceHeader;

    /** 安全 TraceId：仅允许字母、数字、下划线、连字符，长度 1~64 */
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9_\\-]{1,64}");

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 优先复用上游透传的 TraceId，拒绝含 CRLF/控制字符或超长的值（防日志注入 / 响应头分割）
        String traceId = request.getHeaders().getFirst(traceHeader);
        if (!StringUtils.hasText(traceId) || !SAFE_TRACE_ID.matcher(traceId).matches()) {
            traceId = traceIdGenerator.generate();
        }

        final String finalTraceId = traceId;
        MDC.put(WebPlusConstants.MDC_TRACE_ID, finalTraceId);

        RequestContext reqCtx = new RequestContext(
                finalTraceId,
                request.getURI().getPath(),
                request.getMethod().name(),
                null, // IP 由 AccessLogFilter 填充
                request.getHeaders().getFirst("User-Agent"),
                System.currentTimeMillis()
        );
        RequestContextHolder.set(reqCtx);

        // 在响应头回写 TraceId，方便前端上报
        ServerWebExchange mutated = exchange.mutate()
                .response(exchange.getResponse())
                .build();
        mutated.getResponse().getHeaders().set(traceHeader, finalTraceId);

        return chain.filter(mutated)
                .contextWrite(ctx -> ctx.put(RequestContextThreadLocalAccessor.KEY, reqCtx))
                .doFinally(signal -> {
                    MDC.remove(WebPlusConstants.MDC_TRACE_ID);
                    MDC.remove(WebPlusConstants.MDC_USER_ID);
                    MDC.remove(WebPlusConstants.MDC_TENANT_ID);
                    RequestContextHolder.clear();
                });
    }
}

