package io.github.guanxiangkai.jpa.plus.query.wrapper;

import java.io.Serializable;
import java.util.function.Function;

/**
 * 可序列化的类型安全方法引用接口（Lambda 列名推导核心）
 *
 * <p>用于 Lambda 方法引用（如 {@code User::getName}），
 * 通过 {@link java.lang.invoke.SerializedLambda} 序列化机制提取被引用的方法名，
 * 进而由 {@link LambdaColumnResolver} 推导出对应的数据库列名。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * QueryWrapper.from(User.class)
 *     .eq(User::getName, "张三")     // getName → name 列
 *     .ge(User::getAge, 18);         // getAge → age 列
 * }</pre>
 *
 * <p><b>设计模式：</b>类型安全的方法引用（编译期检查列名正确性）</p>
 *
 * <p><b>命名说明：</b>{@code TypedGetter} 表示"带类型约束的 getter 方法引用"，
 * 所有方法引用均应指向实体类的 getter 方法（如 {@code User::getName}）。</p>
 *
 * @param <T> 实体类型
 * @param <R> 返回值类型
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface TypedGetter<T, R> extends Function<T, R>, Serializable {
}
