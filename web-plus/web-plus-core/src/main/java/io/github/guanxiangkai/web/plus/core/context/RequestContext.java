package io.github.guanxiangkai.web.plus.core.context;

/**
 * 请求上下文（当前请求的元信息，存储在 ThreadLocal 中）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public record RequestContext(
        String traceId,
        String requestPath,
        String requestMethod,
        String clientIp,
        String userAgent,
        long requestTime
) {
    public RequestContext {
        if (requestTime <= 0) requestTime = System.currentTimeMillis();
    }
}

