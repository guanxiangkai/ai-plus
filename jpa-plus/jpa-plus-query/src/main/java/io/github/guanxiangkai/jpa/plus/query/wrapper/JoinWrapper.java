package io.github.guanxiangkai.jpa.plus.query.wrapper;

import io.github.guanxiangkai.jpa.plus.query.ast.Condition;
import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;
import io.github.guanxiangkai.jpa.plus.query.metadata.JoinGraph;

import java.util.Collection;

/**
 * Join 查询构造器接口
 *
 * @param <T> 主实体类型
 */
public interface JoinWrapper<T> {

    /**
     * 静态工厂方法
     */
    static <T> JoinWrapper<T> from(Class<T> entityClass) {
        return new DefaultJoinWrapper<>(entityClass);
    }

    /**
     * 为实体类分配别名
     */
    <J> TableAlias<J> as(Class<J> entityClass, String alias);

    /**
     * LEFT JOIN
     */
    <J> JoinWrapper<T> leftJoin(TableAlias<J> right, ColumnAlias<?> leftCol, ColumnAlias<?> rightCol);

    /**
     * INNER JOIN
     */
    <J> JoinWrapper<T> innerJoin(TableAlias<J> right, ColumnAlias<?> leftCol, ColumnAlias<?> rightCol);

    /**
     * RIGHT JOIN
     */
    <J> JoinWrapper<T> rightJoin(TableAlias<J> right, ColumnAlias<?> leftCol, ColumnAlias<?> rightCol);

    /**
     * 选择列
     */
    JoinWrapper<T> select(SelectColumn... columns);

    /**
     * 等于条件
     */
    JoinWrapper<T> eq(ColumnAlias<?> column, Object value);

    /**
     * 不等于条件
     */
    JoinWrapper<T> ne(ColumnAlias<?> column, Object value);

    /**
     * LIKE 条件
     */
    JoinWrapper<T> like(ColumnAlias<?> column, String value);

    /**
     * IN 条件
     */
    JoinWrapper<T> in(ColumnAlias<?> column, Collection<?> values);

    /**
     * 直接添加条件
     */
    JoinWrapper<T> condition(Condition condition);

    /**
     * 升序排序
     */
    JoinWrapper<T> orderByAsc(ColumnAlias<?> column);

    /**
     * 降序排序
     */
    JoinWrapper<T> orderByDesc(ColumnAlias<?> column);

    /**
     * 分页限制
     */
    JoinWrapper<T> limit(int offset, int rows);

    /**
     * 构建连接图
     */
    JoinGraph buildJoinGraph();

    /**
     * 构建查询上下文
     */
    QueryContext buildContext();
}

