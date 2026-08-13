package io.github.guanxiangkai.jpa.plus.query.plan;

import java.lang.invoke.MethodHandle;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 映射计划 —— 预编译的字段映射方案
 * <p>
 * 避免每次查询都反射查找 setter，通过 MethodHandle 直接设置字段值。
 */
public class MappingPlan<R> {

    private final Class<R> targetType;
    private final MethodHandle constructor;
    private final List<FieldMapping> mappings;

    public MappingPlan(Class<R> targetType, MethodHandle constructor, List<FieldMapping> mappings) {
        this.targetType = targetType;
        this.constructor = constructor;
        this.mappings = List.copyOf(mappings);
    }

    /**
     * 从 ResultSet 当前行创建实例并填充字段
     */
    public R apply(ResultSet rs) throws SQLException {
        try {
            R instance = targetType.cast(constructor.invoke());
            for (FieldMapping mapping : mappings) {
                Object value = rs.getObject(mapping.columnIndex(), mapping.fieldType());
                mapping.setValue(instance, value);
            }
            return instance;
        } catch (SQLException e) {
            throw e;
        } catch (Throwable e) {
            throw new SQLException("Failed to map ResultSet row to " + targetType.getSimpleName(), e);
        }
    }

    /**
     * 将缓存中的映射计划恢复为调用方需要的目标类型。
     */
    <T> MappingPlan<T> castTo(Class<T> expectedType) {
        if (!targetType.equals(expectedType)) {
            throw new IllegalStateException("MappingPlan target type mismatch: expected "
                    + expectedType.getName() + " but was " + targetType.getName());
        }
        @SuppressWarnings("unchecked")
        MappingPlan<T> typed = (MappingPlan<T>) this;
        return typed;
    }
}
