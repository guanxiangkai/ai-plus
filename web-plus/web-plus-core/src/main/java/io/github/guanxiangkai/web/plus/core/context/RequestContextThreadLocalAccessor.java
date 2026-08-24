package io.github.guanxiangkai.web.plus.core.context;

import io.github.guanxiangkai.web.plus.core.constant.WebPlusConstants;
import io.micrometer.context.ThreadLocalAccessor;
import org.slf4j.MDC;

/**
 * 请求上下文的 Micrometer ThreadLocal 适配器。
 *
 * <p>Reactor、虚拟线程和任务执行器通过同一个键恢复请求上下文，并同步 MDC 中的 TraceId。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class RequestContextThreadLocalAccessor implements ThreadLocalAccessor<RequestContext> {

    /** Reactor Context 与 Micrometer Context Registry 共用键。 */
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
        } else {
            MDC.remove(WebPlusConstants.MDC_TRACE_ID);
        }
    }

    @Override
    public void setValue() {
        RequestContextHolder.clear();
        MDC.remove(WebPlusConstants.MDC_TRACE_ID);
    }

    @Override
    public void restore(RequestContext previousValue) {
        if (previousValue == null) {
            setValue();
            return;
        }
        setValue(previousValue);
    }
}
