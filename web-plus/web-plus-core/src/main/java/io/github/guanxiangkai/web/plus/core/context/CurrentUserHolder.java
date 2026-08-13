package io.github.guanxiangkai.web.plus.core.context;

/**
 * 当前用户上下文持有者（ThreadLocal）
 * <p>
 * 由认证过滤器在请求入口设置，业务代码通过 {@code SecurityUtils} 读取。
 * 响应式链路同时将用户写入 Reactor Context（key: {@link #REACTOR_CONTEXT_KEY}），
 * 供 {@code ReactiveCurrentUserProvider} 和 {@code Mono.deferContextual} 读取。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class CurrentUserHolder {

    /**
     * Reactor Context 键名，用于在响应式订阅链路中传播当前用户。
     * <p>由 {@code TokenAuthenticationFilter} 写入，{@code ReactiveCurrentUserProvider} 读取。</p>
     */
    public static final String REACTOR_CONTEXT_KEY = "web-plus.currentUser";

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static boolean exists() {
        return HOLDER.get() != null;
    }

    public static void clear() {
        HOLDER.remove();
    }
}

