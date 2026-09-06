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
     * 设置字段值。
     *
     * <p>引用类型的 SQL {@code NULL} 必须覆盖目标对象的初始值；基本类型保留构造器初始化值，
     * 避免向其 MethodHandle 传入 {@code null}。</p>
     *
     * @param instance 接收字段值的结果对象
     * @param value JDBC 读取的列值，可为 {@code null}
     * @throws Throwable setter 或字段句柄执行失败时原样传播
     */
    public void setValue(Object instance, Object value) throws Throwable {
        if (value != null || !fieldType.isPrimitive()) {
            setter.invoke(instance, value);
        }
    }
}
