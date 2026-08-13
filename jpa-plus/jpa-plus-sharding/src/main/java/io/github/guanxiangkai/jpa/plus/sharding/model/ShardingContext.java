package io.github.guanxiangkai.jpa.plus.sharding.model;

import java.util.concurrent.Callable;

/**
 * 分片路由上下文
 *
 * <p>基于 JDK 21+ {@link ScopedValue} 存储当前请求的分片路由目标，天然支持虚拟线程，
 * 无需手动清理，作用域结束后自动失效。</p>
 *
 * <p>由 {@link io.github.guanxiangkai.jpa.plus.sharding.interceptor.ShardingInterceptor} 在 BEFORE 阶段写入，
 * 下游代码（如 AST 改写器、表名替换器）可通过 {@link #current()} 读取当前路由目标。</p>
 *
 * <p><b>设计模式：</b>上下文对象模式（Context Object）—— 参照 JpaPlusContext 设计</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class ShardingContext {

    private static final ScopedValue<ShardingTarget> CURRENT = ScopedValue.newInstance();

    private ShardingContext() {
    }

    /**
     * 在指定分片目标上下文中执行有返回值的任务
     *
     * <p>利用 JDK 25 {@link ScopedValue.Carrier#call(ScopedValue.CallableOp)} 泛型重载，
     * 任意 {@code Throwable} 均可透传，无需 {@code ThrowableWrapper} 二次包装。</p>
     *
     * @param target 分片路由目标
     * @param task   待执行任务
     * @param <R>    返回类型
     * @return 任务执行结果
     * @throws Throwable 执行异常（原样透传，无任何包装）
     */
    public static <R> R withTarget(ShardingTarget target, ThrowableCallable<R> task) throws Throwable {
        return ScopedValue.where(CURRENT, target).call(task::call);
    }

    /**
     * 在指定分片目标上下文中执行有返回值的任务（仅 Exception 版，供常规代码使用）
     *
     * @param target 分片路由目标
     * @param task   待执行任务
     * @param <R>    返回类型
     * @return 任务执行结果
     * @throws Exception 执行异常
     */
    public static <R> R withTarget(ShardingTarget target, Callable<R> task) throws Exception {
        return ScopedValue.where(CURRENT, target).call(task::call);
    }

    /**
     * 获取当前分片路由目标
     *
     * @return 当前分片目标，若未设置则返回 {@code null}
     */
    public static ShardingTarget current() {
        return CURRENT.isBound() ? CURRENT.get() : null;
    }

    /**
     * 是否已设置分片上下文
     */
    public static boolean isSet() {
        return CURRENT.isBound();
    }

    /**
     * 允许抛出 Throwable 的 Callable（与 {@link ScopedValue.CallableOp} 结构兼容）
     */
    @FunctionalInterface
    public interface ThrowableCallable<R> {
        R call() throws Throwable;
    }
}

