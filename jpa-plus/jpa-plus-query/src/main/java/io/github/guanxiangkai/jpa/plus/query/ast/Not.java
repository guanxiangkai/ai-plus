package io.github.guanxiangkai.jpa.plus.query.ast;

/**
 * NOT 条件：NOT condition
 */
public record Not(Condition condition) implements Condition {
    @Override
    public <R> R accept(ConditionVisitor<R> visitor) {
        return visitor.visit(this);
    }
}

