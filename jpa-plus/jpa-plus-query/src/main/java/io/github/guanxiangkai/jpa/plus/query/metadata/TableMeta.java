package io.github.guanxiangkai.jpa.plus.query.metadata;

import io.github.guanxiangkai.jpa.plus.core.util.NamingUtils;
import jakarta.persistence.Table;

/**
 * 表元数据
 *
 * @param entityClass 实体类
 * @param tableName   数据库表名
 * @param alias       别名
 */
public record TableMeta(Class<?> entityClass, String tableName, String alias) {

    private static final ClassValue<String> TABLE_NAME_CACHE = new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> type) {
            Table table = type.getAnnotation(Table.class);
            if (table != null && !table.name().isEmpty()) {
                return table.name();
            }
            return NamingUtils.camelToSnake(type.getSimpleName());
        }
    };

    /**
     * 根据实体类和别名创建表元数据
     * <p>
     * 优先读取 @Table 注解的 name 属性，否则使用类名的蛇形命名
     */
    public static TableMeta of(Class<?> entityClass, String alias) {
        String tableName = resolveTableName(entityClass);
        return new TableMeta(entityClass, tableName, alias);
    }

    /**
     * 使用默认别名（表名首字母小写）
     */
    public static TableMeta of(Class<?> entityClass) {
        String tableName = resolveTableName(entityClass);
        String defaultAlias = tableName.substring(0, 1).toLowerCase() + tableName.substring(1);
        return new TableMeta(entityClass, tableName, defaultAlias);
    }

    private static String resolveTableName(Class<?> entityClass) {
        return TABLE_NAME_CACHE.get(entityClass);
    }
}
