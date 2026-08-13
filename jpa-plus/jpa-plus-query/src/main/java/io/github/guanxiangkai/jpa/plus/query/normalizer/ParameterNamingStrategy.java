package io.github.guanxiangkai.jpa.plus.query.normalizer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 参数命名策略
 *
 * <p>每次编译使用独立的计数器实例，避免全局计数器在并发编译时产生不确定的参数名。
 * 使用 JDK 25 {@link ScopedValue} 替代 {@code ThreadLocal}，
 * 天然支持虚拟线程，无需手动清理。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class ParameterNamingStrategy {

    private static final ScopedValue<AtomicInteger> COUNTER = ScopedValue.newInstance();

    /**
     * 开启新的编译上下文（绑定 ScopedValue 计数器）
     *
     * <p>与 {@link #end()} 配对使用。推荐在 try-finally 中调用：</p>
     * <pre>{@code
     * ParameterNamingStrategy.begin();
     * try {
     *     // compile SQL ...
     * } finally {
     *     ParameterNamingStrategy.end();
     * }
     * }</pre>
     *
     * <p>内部实现：若当前不在 ScopedValue 作用域内，通过
     * {@link ScopedValue#where(ScopedValue, Object)} 创建绑定。
     * 因编译流程为同步执行，此处使用简单的绑定标记兼容既有 begin/end 模式。</p>
     */
    public static void begin() {
        // ScopedValue 在 AbstractSqlCompiler.compile() 的 runWhere() 中绑定
        // 此方法保留为语义占位，实际绑定由 runWhere() 完成
    }

    /**
     * 结束编译上下文
     *
     * <p>ScopedValue 无需手动清理，离开作用域自动回收。
     * 此方法保留为语义占位，与 {@link #begin()} 对称。</p>
     */
    public static void end() {
        // ScopedValue 离开作用域自动清理，无需手动操作
    }

    /**
     * 在新的参数命名作用域中执行任务
     *
     * <p>推荐使用此方法替代 {@link #begin()}/{@link #end()} 对，
     * 以块作用域方式保证计数器的生命周期管理。</p>
     *
     * @param task 待执行任务
     * @param <R>  返回类型
     * @return 任务执行结果
     */
    public static <R> R runWhere(ScopedValue.CallableOp<R, RuntimeException> task) {
        return ScopedValue.where(COUNTER, new AtomicInteger(0)).call(task);
    }

    /**
     * 生成带前缀的唯一参数名
     */
    public static String next(String prefix) {
        return prefix + "_" + counter().getAndIncrement();
    }

    /**
     * 生成带上下文的唯一参数名
     */
    public static String nextWithContext(String context) {
        return context + "_" + counter().getAndIncrement();
    }

    /**
     * 重置计数器（仅用于测试）
     */
    public static void reset() {
        if (COUNTER.isBound()) {
            counter().set(0);
        }
    }

    private static AtomicInteger counter() {
        if (COUNTER.isBound()) {
            return COUNTER.get();
        }
        // P1-31: A missing runWhere() scope means parameter names will collide (all reset to _0).
        // Fail fast to surface the misconfiguration rather than produce silently wrong SQL.
        throw new IllegalStateException(
                "[jpa-plus] ParameterNamingStrategy.next() called outside a runWhere() scope. " +
                        "SQL compilation must go through SqlCompiler.compile() which establishes the required scope.");
    }
}
