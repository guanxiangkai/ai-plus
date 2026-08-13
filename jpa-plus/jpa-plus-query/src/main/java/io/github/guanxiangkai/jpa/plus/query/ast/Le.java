package io.github.guanxiangkai.jpa.plus.query.ast;

import io.github.guanxiangkai.jpa.plus.query.metadata.ColumnMeta;

/**
 * 小于等于条件：{@code column <= value}
 */
public record Le(ColumnMeta column, Object value) implements Condition {
    @Override
    public <R> R accept(ConditionVisitor<R> visitor) {
        return visitor.visit(this);
    }
}

