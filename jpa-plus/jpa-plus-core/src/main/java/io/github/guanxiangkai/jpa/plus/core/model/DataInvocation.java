package io.github.guanxiangkai.jpa.plus.core.model;

import java.lang.reflect.Method;

/**
 * 数据调用封装（密封接口，不可变值对象）
 *
 * <p>采用 sealed interface 替代单一 record，将三种操作类型建模为独立的不可变值对象，
 * 通过模式匹配（pattern matching）取代 {@code invocation.type()} 枚举判断，
 * 提供编译期的分支完整性保障，消除 {@code entity}/{@code queryModel} 混用问题。</p>
 *
 * <h3>子类型</h3>
 * <ul>
 *   <li>{@link QueryInvocation}  — 查询操作，携带 {@code queryContext}</li>
 *   <li>{@link SaveInvocation}   — 新增/更新操作，携带 {@code entity}</li>
 *   <li>{@link DeleteInvocation} — 删除操作，携带 {@code entity}</li>
 * </ul>
 *
 * <p><b>设计模式：</b>不可变值对象 + 密封类型层次（Immutable Value Object + Sealed Hierarchy）</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public sealed interface DataInvocation permits QueryInvocation, SaveInvocation, DeleteInvocation {

    /**
     * 操作类型（用于预编译拦截器链的快速路由）
     */
    OperationType type();

    /**
     * 实体类型（用于元数据解析、字段引擎）
     */
    Class<?> entityClass();

    /**
     * 触发此次调用的 Repository 方法（可为 {@code null}）
     */
    Method repositoryMethod();

    /**
     * 返回附加了 Repository 方法的新实例（不可变模式）
     */
    DataInvocation withRepositoryMethod(Method method);
}
