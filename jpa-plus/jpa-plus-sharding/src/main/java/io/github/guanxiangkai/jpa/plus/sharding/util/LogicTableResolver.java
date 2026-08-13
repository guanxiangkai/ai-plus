package io.github.guanxiangkai.jpa.plus.sharding.util;

import jakarta.persistence.Table;

/**
 * 逻辑表名解析工具
 *
 * <p>从实体类解析对应的逻辑表名，优先读取 {@code @Table(name=...)} 注解，
 * 若未标注则将类名转换为下划线小写形式（如 {@code OrderItem} → {@code order_item}）。</p>
 *
 * <p>解析结果会被缓存，避免重复反射开销。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class LogicTableResolver {

    private static final ClassValue<String> CACHE = new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> type) {
            return doResolve(type);
        }
    };

    private LogicTableResolver() {
    }

    /**
     * 解析实体类对应的逻辑表名
     *
     * @param entityClass 实体类
     * @return 逻辑表名（小写下划线形式）
     */
    public static String resolve(Class<?> entityClass) {
        return CACHE.get(entityClass);
    }

    private static String doResolve(Class<?> entityClass) {
        // ① 尝试读取 jakarta.persistence.Table 注解的 name 属性
        Table table = entityClass.getAnnotation(Table.class);
        if (table != null && !table.name().isBlank()) {
            return table.name().toLowerCase();
        }

        // ② 类名转下划线小写（如 OrderItem → order_item）
        return toSnakeCase(entityClass.getSimpleName());
    }

    /**
     * 驼峰转下划线小写，例如：OrderItem → order_item
     */
    static String toSnakeCase(String className) {
        if (className == null || className.isEmpty()) return className;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < className.length(); i++) {
            char c = className.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

}
