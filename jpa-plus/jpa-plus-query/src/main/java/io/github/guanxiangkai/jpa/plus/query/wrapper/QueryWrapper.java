package io.github.guanxiangkai.jpa.plus.query.wrapper;

import io.github.guanxiangkai.jpa.plus.query.ast.AggregateCondition;
import io.github.guanxiangkai.jpa.plus.query.ast.Condition;
import io.github.guanxiangkai.jpa.plus.query.ast.SubQuery;
import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * 查询条件构造器接口（Lambda DSL 入口）
 *
 * <p>提供类型安全的链式 API 构造查询条件，支持：
 * <ul>
 *   <li>比较条件：{@code eq / ne / gt / ge / lt / le}</li>
 *   <li>模糊查询：{@code like / likeLeft / likeRight}</li>
 *   <li>范围查询：{@code in / between}</li>
 *   <li>逻辑组合：{@code and / or / not}</li>
 *   <li>子查询：{@code exists / subQuery}</li>
 *   <li>排序/分页：{@code orderByAsc / orderByDesc / limit}</li>
 *   <li>投影：{@code select}</li>
 * </ul>
 * </p>
 *
 * <p><b>设计模式：</b>建造者模式（Builder） —— 链式 API 构建复杂查询条件</p>
 *
 * @param <T> 实体类型
 * @author guanxiangkai
 * @see DefaultQueryWrapper
 * @see TypedGetter
 * @since 1.0.0
 */
public interface QueryWrapper<T> {

    /**
     * 静态工厂方法
     */
    static <T> QueryWrapper<T> from(Class<T> entityClass) {
        return new DefaultQueryWrapper<>(entityClass);
    }

    /**
     * 等于条件
     */
    QueryWrapper<T> eq(TypedGetter<T, ?> column, Object value);

    /**
     * 不等于条件
     */
    QueryWrapper<T> ne(TypedGetter<T, ?> column, Object value);

    /**
     * 大于条件
     */
    QueryWrapper<T> gt(TypedGetter<T, ?> column, Object value);

    /**
     * 大于等于条件
     */
    QueryWrapper<T> ge(TypedGetter<T, ?> column, Object value);

    /**
     * 小于条件
     */
    QueryWrapper<T> lt(TypedGetter<T, ?> column, Object value);

    /**
     * 小于等于条件
     */
    QueryWrapper<T> le(TypedGetter<T, ?> column, Object value);

    /**
     * LIKE 模糊查询（默认 ANYWHERE 模式）
     */
    QueryWrapper<T> like(TypedGetter<T, ?> column, String value);

    /**
     * IN 条件
     */
    QueryWrapper<T> in(TypedGetter<T, ?> column, Collection<?> values);

    /**
     * BETWEEN 条件
     */
    QueryWrapper<T> between(TypedGetter<T, ?> column, Object start, Object end);

    /**
     * AND 嵌套条件
     */
    QueryWrapper<T> and(Consumer<QueryWrapper<T>> consumer);

    /**
     * OR 嵌套条件
     */
    QueryWrapper<T> or(Consumer<QueryWrapper<T>> consumer);

    /**
     * 直接添加 AST 条件
     */
    QueryWrapper<T> condition(Condition condition);

    /**
     * EXISTS 子查询
     */
    QueryWrapper<T> exists(SubQuery subQuery);

    /**
     * 升序排序
     */
    QueryWrapper<T> orderByAsc(TypedGetter<T, ?> column);

    /**
     * 降序排序
     */
    QueryWrapper<T> orderByDesc(TypedGetter<T, ?> column);

    /**
     * 分页限制
     */
    QueryWrapper<T> limit(int offset, int rows);

    /**
     * GROUP BY 分组
     *
     * <p>传入一个或多个 Lambda 列引用，生成 {@code GROUP BY col1, col2} 子句。</p>
     */
    @SuppressWarnings("unchecked")
    QueryWrapper<T> groupBy(TypedGetter<T, ?>... columns);

    /**
     * HAVING 过滤（原子条件）
     *
     * <p>追加到 HAVING 子句，多次调用时用 AND 合并。通常与聚合条件配合使用：</p>
     * <pre>{@code
     * .groupBy(Order::getUserId)
     * .having(new AggregateCondition(AggregateFunction.COUNT, null, Operator.GT, 5))
     * }</pre>
     */
    QueryWrapper<T> having(AggregateCondition condition);

    /**
     * 构建条件表达式树
     */
    Condition buildCondition();

    /**
     * 构建完整查询上下文
     */
    QueryContext buildContext();

    /**
     * 获取实体类
     */
    Class<T> getEntityClass();
}
