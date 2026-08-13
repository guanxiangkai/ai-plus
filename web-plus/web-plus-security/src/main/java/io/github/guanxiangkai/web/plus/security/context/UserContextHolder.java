package io.github.guanxiangkai.web.plus.security.context;

/**
 * 用户上下文持有者（ThreadLocal）
 * <p>
 * 配合 {@link UserContextThreadLocalAccessor} 实现 Reactor 线程自动传播：
 * <ol>
 *   <li>{@code HeaderAuthenticationFilter} 从请求头解析用户信息，调用 {@link #set(UserContext)}</li>
 *   <li>Micrometer Context Propagation 自动在 Reactor 线程切换时 capture → restore</li>
 *   <li>Service 层通过 {@link #get()} 同步获取当前用户上下文</li>
 * </ol>
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContext ctx) {
        HOLDER.set(ctx);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}