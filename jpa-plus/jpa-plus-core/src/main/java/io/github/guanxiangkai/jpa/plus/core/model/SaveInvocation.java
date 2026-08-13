package io.github.guanxiangkai.jpa.plus.core.model;

import java.lang.reflect.Method;

/**
 * 新增/更新操作调用封装
 *
 * @param entityClass      实体类型
 * @param entity           实体对象（可为 {@link java.util.List} 表示批量操作）
 * @param repositoryMethod 触发调用的 Repository 方法（可为 {@code null}）
 * @author guanxiangkai
 * @since 1.0.0
 */
public record SaveInvocation(
        Class<?> entityClass,
        Object entity,
        Method repositoryMethod
) implements DataInvocation {

    public SaveInvocation(Class<?> entityClass, Object entity) {
        this(entityClass, entity, null);
    }

    @Override
    public OperationType type() {
        return OperationType.SAVE;
    }

    @Override
    public SaveInvocation withRepositoryMethod(Method method) {
        return new SaveInvocation(entityClass, entity, method);
    }
}
