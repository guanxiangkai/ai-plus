package io.github.guanxiangkai.jpa.plus.core.model;

import java.lang.reflect.Method;

/**
 * 查询操作调用封装
 *
 * <p>{@code queryContext} 字段实际类型为 {@code QueryContext}（位于 jpa-plus-query 模块）。
 * 此处声明为 {@code Object} 以避免 core → query 循环依赖；
 * 在 query/interceptor/starter 等下游模块中可通过 {@code instanceof QueryContext} 安全向下转型。</p>
 *
 * @param entityClass      实体类型
 * @param queryContext     查询上下文（{@code QueryContext} 实例）
 * @param repositoryMethod 触发调用的 Repository 方法（可为 {@code null}）
 * @author guanxiangkai
 * @since 1.0.0
 */
public record QueryInvocation(
        Class<?> entityClass,
        Object queryContext,
        Method repositoryMethod
) implements DataInvocation {

    public QueryInvocation(Class<?> entityClass, Object queryContext) {
        this(entityClass, queryContext, null);
    }

    @Override
    public OperationType type() {
        return OperationType.QUERY;
    }

    /**
     * 替换 queryContext 生成新的 QueryInvocation（不可变模式）
     */
    public QueryInvocation withQueryContext(Object newContext) {
        return new QueryInvocation(entityClass, newContext, repositoryMethod);
    }

    @Override
    public QueryInvocation withRepositoryMethod(Method method) {
        return new QueryInvocation(entityClass, queryContext, method);
    }
}
