package io.github.guanxiangkai.jpa.plus.query.ast;

/**
 * EXISTS 条件：EXISTS (subquery)
 */
public record Exists(SubQuery subQuery) implements Condition {
    @Override
    public <R> R accept(ConditionVisitor<R> visitor) {
        return visitor.visit(this);
    }
}

