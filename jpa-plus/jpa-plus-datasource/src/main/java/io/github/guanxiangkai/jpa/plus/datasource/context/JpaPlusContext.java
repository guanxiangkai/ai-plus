package io.github.guanxiangkai.jpa.plus.datasource.context;

import io.github.guanxiangkai.jpa.plus.datasource.enums.DsName;

import java.util.concurrent.Callable;

/**
 * JPA-Plus 多数据源上下文
 *
 * <p>使用 JDK 25 {@link ScopedValue}（替代 ThreadLocal）存储当前数据源名称，
 * 天然支持虚拟线程，无需手动清理。</p>
 *
 * <p><b>设计模式：</b>上下文对象模式（Context Object） —— 通过 ScopedValue 传递线程范围状态</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * JpaPlusContext.withDS("slave", () -> {
 *     return userRepo.findById(1L);
 * });
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class JpaPlusContext {

    /**
     * 当前数据源名称（ScopedValue，线程安全且虚拟线程友好）
     */
    private static final ScopedValue<String> CURRENT_DS = ScopedValue.newInstance();

    private JpaPlusContext() {
    }

    /**
     * 在指定数据源上下文中执行有返回值的操作
     *
     * @param dsName 数据源名称
     * @param task   待执行任务
     * @param <R>    返回类型
     * @return 任务执行结果
     * @throws Exception 执行异常
     */
    public static <R> R withDS(String dsName, Callable<R> task) throws Exception {
        return ScopedValue.where(CURRENT_DS, dsName).call(task::call);
    }

    /**
     * 在指定数据源上下文中执行可能抛出 Throwable 的操作
     *
     * <p>主要供 AOP 切面使用（{@code ProceedingJoinPoint.proceed()} 声明抛出 {@code Throwable}）。</p>
     *
     * @param dsName 数据源名称
     * @param task   待执行任务（允许抛出 Throwable）
     * @param <R>    返回类型
     * @return 任务执行结果
     * @throws Throwable 执行异常
     */
    @SuppressWarnings("unchecked")
    public static <R> R withDS(String dsName, ThrowableCallable<R> task) throws Throwable {
        try {
            return ScopedValue.where(CURRENT_DS, dsName).call(() -> {
                try {
                    return task.call();
                } catch (Exception e) {
                    throw e;
                } catch (Throwable t) {
                    throw new ThrowableWrapper(t);
                }
            });
        } catch (ThrowableWrapper wrapper) {
            throw wrapper.getCause();
        }
    }

    /**
     * 获取当前数据源名称（使用默认 primary="master"）
     *
     * @return 数据源名称，默认返回 "master"
     */
    public static String currentDS() {
        return CURRENT_DS.orElse(DsName.MASTER);
    }

    /**
     * 获取当前数据源名称（使用指定的 primary 名称作为默认值）
     *
     * @param defaultName 无绑定时的默认数据源名称
     * @return 数据源名称
     */
    public static String currentDS(String defaultName) {
        return CURRENT_DS.orElse(defaultName);
    }

    /**
     * 在指定数据源上下文中执行无返回值的操作
     *
     * @param dsName 数据源名称
     * @param task   待执行任务
     */
    public static void runWithDS(String dsName, Runnable task) {
        ScopedValue.where(CURRENT_DS, dsName).run(task);
    }

    /**
     * 允许抛出 Throwable 的 Callable（供 AOP 场景使用）
     */
    @FunctionalInterface
    public interface ThrowableCallable<R> {
        R call() throws Throwable;
    }

    /**
     * Throwable 包装器（内部使用，将 Throwable 透传过 Callable 边界）
     */
    private static class ThrowableWrapper extends RuntimeException {
        ThrowableWrapper(Throwable cause) {
            super(cause);
        }
    }
}

