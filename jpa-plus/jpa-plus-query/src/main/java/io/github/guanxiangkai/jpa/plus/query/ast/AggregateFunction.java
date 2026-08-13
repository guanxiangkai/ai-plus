package io.github.guanxiangkai.jpa.plus.query.ast;

/**
 * 聚合函数枚举
 *
 * <p>用于构造 HAVING 子句中的聚合条件，例如 {@code COUNT(*) > 5}、{@code SUM(amount) >= 100}。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public enum AggregateFunction {

    /**
     * COUNT：行数统计，对应 {@code COUNT(*)} 或 {@code COUNT(col)}
     */
    COUNT,

    /**
     * SUM：数值求和
     */
    SUM,

    /**
     * MAX：最大值
     */
    MAX,

    /**
     * MIN：最小值
     */
    MIN,

    /**
     * AVG：平均值
     */
    AVG;

    /**
     * 生成聚合函数 SQL 片段
     *
     * @param columnQualifiedName 列的全限定名（带表别名），为 {@code null} 时表示 {@code *}
     * @return 聚合表达式字符串，如 {@code COUNT(*)}、{@code SUM(t.amount)}
     */
    public String toSql(String columnQualifiedName) {
        String col = columnQualifiedName == null ? "*" : columnQualifiedName;
        return name() + "(" + col + ")";
    }
}

