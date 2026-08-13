package io.github.guanxiangkai.jpa.plus.query.metadata;

/**
 * 列元数据
 *
 * @param columnName 数据库列名
 * @param alias      列别名
 * @param table      所属表元数据
 * @param javaType   Java 字段类型
 */
public record ColumnMeta(String columnName, String alias, TableMeta table, Class<?> javaType) {

    /**
     * 工厂方法
     */
    public static ColumnMeta of(TableMeta table, String columnName, Class<?> javaType) {
        return new ColumnMeta(columnName, columnName, table, javaType);
    }

    /**
     * 获取带表别名的全限定列名
     */
    public String qualifiedName() {
        return table.alias() + "." + columnName;
    }

    /**
     * 指定别名
     */
    public ColumnMeta as(String newAlias) {
        return new ColumnMeta(columnName, newAlias, table, javaType);
    }
}

