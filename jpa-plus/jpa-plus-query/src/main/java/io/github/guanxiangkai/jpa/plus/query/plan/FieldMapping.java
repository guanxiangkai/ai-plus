package io.github.guanxiangkai.jpa.plus.query.plan;

import java.lang.invoke.MethodHandle;

/**
 * 字段映射 —— 描述 ResultSet 列到 Java 对象字段的映射
 *
 * @param columnName  列名
 * @param columnIndex 列索引
 * @param setter      setter MethodHandle
 * @param fieldType   字段类型
 */
public record FieldMapping(
        String columnName,
        int columnIndex,
        MethodHandle setter,
        Class<?> fieldType
) {

    /**
     * 设置字段值
     */
    public void setValue(Object instance, Object value) throws Throwable {
        if (value != null) {
            setter.invoke(instance, value);
        }
    }
}

