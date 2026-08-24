package io.github.guanxiangkai.web.plus.log.client;

import io.github.guanxiangkai.web.plus.core.context.RequestContext;
import io.github.guanxiangkai.web.plus.core.context.RequestContextHolder;
import io.github.guanxiangkai.web.plus.core.trace.TraceId;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 为自动配置的 WebClient 追加自定义 TraceId 请求头。
 *
 * <p>标准 {@code traceparent} 由 Micrometer Tracing 自动注入；本过滤器仅补充面向前端和日志检索的
 * {@code X-Trace-Id}，并且不会覆盖调用方显式设置的值。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class TraceIdWebClientCustomizer implements WebClientCustomizer {

    private final String traceHeader;

    /**
     * 创建 TraceId WebClient 定制器。
     *
     * @param traceHeader TraceId HTTP 请求头名称
     */
    public TraceIdWebClientCustomizer(String traceHeader) {
        this.traceHeader = traceHeader;
    }

    @Override
    public void customize(WebClient.Builder builder) {
        builder.filter((request, next) -> Mono.deferContextual(contextView -> {
            if (request.headers().containsHeader(traceHeader)) {
                return next.exchange(request);
            }
            RequestContext requestContext = contextView.getOrDefault(
                    RequestContextHolder.REACTOR_CONTEXT_KEY,
                    RequestContextHolder.get()
            );
            String traceId = requestContext == null ? null : requestContext.traceId();
            if (!TraceId.isValid(traceId)) {
                return next.exchange(request);
            }
            ClientRequest tracedRequest = ClientRequest.from(request)
                    .headers(headers -> headers.set(traceHeader, traceId))
                    .build();
            return next.exchange(tracedRequest);
        }));
    }
}
