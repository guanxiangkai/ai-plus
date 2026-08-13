package io.github.guanxiangkai.jpa.plus.query.ast;

import io.github.guanxiangkai.jpa.plus.query.metadata.ColumnMeta;

/**
 * 聚合条件（HAVING 子句专用）
 *
 * <p>表达式形如 {@code COUNT(*) > 5}、{@code SUM(amount) >= 100.0}，
 * 用于 {@link io.github.guanxiangkai.jpa.plus.query.context.QueryRuntime#having()} 字段，
 * 由 SQL 编译器输出到 {@code HAVING} 子句中。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // HAVING COUNT(*) > 5
 * new AggregateCondition(AggregateFunction.COUNT, null, Operator.GT, 5)
 *
 * // HAVING SUM(t.amount) >= 100.0
 * new AggregateCondition(AggregateFunction.SUM, amountColumn, Operator.GE, 100.0)
 * }</pre>
 *
 * @param function 聚合函数类型（COUNT / SUM / MAX / MIN / AVG）
 * @param column   聚合列（{@code null} 表示 {@code *}，用于 COUNT(*)）
 * @param operator 比较运算符
 * @param value    比较值
 * @author guanxiangkai
 * @since 1.0.0
 */
public record AggregateCondition(
        AggregateFunction function,
        ColumnMeta column,
        Operator operator,
        Object value
) implements Condition {

    @Override
    public <R> R accept(ConditionVisitor<R> visitor) {
        return visitor.visit(this);
    }
}

