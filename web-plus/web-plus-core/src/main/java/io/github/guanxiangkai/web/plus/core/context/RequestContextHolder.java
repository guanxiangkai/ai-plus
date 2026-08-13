package io.github.guanxiangkai.web.plus.core.context;

/**
 * 请求上下文持有者（ThreadLocal）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class RequestContextHolder {

    /**
     * Reactor Context 键名，与 {@code RequestContextThreadLocalAccessor} 共享，
     * 用于在响应式订阅链路中传播请求上下文。
     */
    public static final String REACTOR_CONTEXT_KEY = "web-plus.requestContext";

    private static final ThreadLocal<RequestContext> HOLDER = new ThreadLocal<>();

    private RequestContextHolder() {
    }

    public static void set(RequestContext ctx) {
        HOLDER.set(ctx);
    }

    public static RequestContext get() {
        return HOLDER.get();
    }

    public static String getTraceId() {
        RequestContext ctx = HOLDER.get();
        return ctx != null ? ctx.traceId() : null;
    }

    public static void clear() {
        HOLDER.remove();
    }
}

