package io.github.guanxiangkai.web.plus.log.context;

import io.github.guanxiangkai.web.plus.core.constant.WebPlusConstants;
import io.github.guanxiangkai.web.plus.core.context.RequestContext;
import io.github.guanxiangkai.web.plus.core.context.RequestContextHolder;
import io.micrometer.context.ThreadLocalAccessor;
import org.slf4j.MDC;

/**
 * Micrometer Context Propagation 适配器 —— 请求上下文
 * <p>
 * 注册到 {@link io.micrometer.context.ContextRegistry} 后，
 * Reactor 在 {@code publishOn/subscribeOn} 切换线程时自动 capture → restore
 * {@link RequestContextHolder} 中的 {@link RequestContext}，同时同步 MDC traceId，
 * 确保跨线程（如 JPA boundedElastic 调度）的日志、访问日志均能正确携带 TraceId。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class RequestContextThreadLocalAccessor implements ThreadLocalAccessor<RequestContext> {

    public static final String KEY = RequestContextHolder.REACTOR_CONTEXT_KEY;

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public RequestContext getValue() {
        return RequestContextHolder.get();
    }

    @Override
    public void setValue(RequestContext value) {
        RequestContextHolder.set(value);
        if (value != null && value.traceId() != null) {
            MDC.put(WebPlusConstants.MDC_TRACE_ID, value.traceId());
        }
    }

    @Override
    public void setValue() {
        RequestContextHolder.clear();
        MDC.remove(WebPlusConstants.MDC_TRACE_ID);
    }

    @Override
    public void restore(RequestContext previousValue) {
        if (previousValue != null) {
            setValue(previousValue);
        } else {
            setValue();
        }
    }
}
