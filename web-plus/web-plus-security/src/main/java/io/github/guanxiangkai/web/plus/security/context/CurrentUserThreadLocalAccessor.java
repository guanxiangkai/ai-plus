package io.github.guanxiangkai.web.plus.security.context;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.micrometer.context.ThreadLocalAccessor;

/**
 * Micrometer Context Propagation 适配器
 * <p>
 * 实现此接口后，注册到 {@link io.micrometer.context.ContextRegistry}，
 * Reactor 在 {@code publishOn/subscribeOn} 切换线程时会自动 capture → restore
 * ThreadLocal 中的 {@link CurrentUser}，使得虚拟线程/响应式链路均可正确传播用户上下文。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class CurrentUserThreadLocalAccessor
        implements ThreadLocalAccessor<CurrentUser> {

    public static final String KEY = CurrentUserHolder.REACTOR_CONTEXT_KEY;

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public CurrentUser getValue() {
        return CurrentUserHolder.get();
    }

    @Override
    public void setValue(CurrentUser value) {
        CurrentUserHolder.set(value);
    }

    @Override
    public void setValue() {
        CurrentUserHolder.clear();
    }

    @Override
    public void restore(CurrentUser previousValue) {
        if (previousValue != null) {
            CurrentUserHolder.set(previousValue);
        } else {
            CurrentUserHolder.clear();
        }
    }
}

