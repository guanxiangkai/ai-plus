package io.github.guanxiangkai.web.plus.security.context;

import io.micrometer.context.ThreadLocalAccessor;

/**
 * 将 {@link UserContextHolder} 的 ThreadLocal 注册到 Micrometer Context Propagation，
 * 使其在 Reactor 线程切换（{@code publishOn} / {@code subscribeOn}）时自动传播。
 * <p>
 * 通过 SPI（{@code META-INF/services/io.micrometer.context.ThreadLocalAccessor}）自动注册。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class UserContextThreadLocalAccessor implements ThreadLocalAccessor<UserContext> {

    /**
     * 在 Reactor Context 中存储 UserContext 的 key
     */
    public static final String KEY = "ai.userContext";

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public UserContext getValue() {
        return UserContextHolder.get();
    }

    @Override
    public void setValue(UserContext value) {
        UserContextHolder.set(value);
    }

    @Override
    public void setValue() {
        UserContextHolder.clear();
    }
}

