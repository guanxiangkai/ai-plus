package io.github.guanxiangkai.jpa.plus.query.pagination;

/**
 * COUNT 查询优化策略
 */
public enum CountStrategy {
    /**
     * 简单查询：直接 count，去掉 order by 和 limit
     */
    SIMPLE,
    /**
     * 有 join 但不影响行数：使用子查询包装
     */
    SUBQUERY,
    /**
     * 有 group by / distinct：强制子查询
     */
    FORCE_SUBQUERY
}

