package io.github.guanxiangkai.jpa.plus.core.model;

import java.lang.reflect.Method;

/**
 * 删除操作调用封装
 *
 * @param entityClass      实体类型
 * @param entity           被删除的实体对象
 * @param repositoryMethod 触发调用的 Repository 方法（可为 {@code null}）
 * @author guanxiangkai
 * @since 1.0.0
 */
public record DeleteInvocation(
        Class<?> entityClass,
        Object entity,
        Method repositoryMethod
) implements DataInvocation {

    public DeleteInvocation(Class<?> entityClass, Object entity) {
        this(entityClass, entity, null);
    }

    @Override
    public OperationType type() {
        return OperationType.DELETE;
    }

    @Override
    public DeleteInvocation withRepositoryMethod(Method method) {
        return new DeleteInvocation(entityClass, entity, method);
    }
}
