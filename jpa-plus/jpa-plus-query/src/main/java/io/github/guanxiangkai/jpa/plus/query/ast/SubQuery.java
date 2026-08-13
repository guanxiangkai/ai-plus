package io.github.guanxiangkai.jpa.plus.query.ast;

import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;

/**
 * 子查询条件
 */
public record SubQuery(QueryContext query, Operator operator) implements Condition {
    @Override
    public <R> R accept(ConditionVisitor<R> visitor) {
        return visitor.visit(this);
    }
}

