package io.github.guanxiangkai.jpa.plus.query.ast;

import java.util.List;

/**
 * AND 组合条件：condition1 AND condition2 AND ...
 */
public record And(List<Condition> conditions) implements Condition {

    public And(Condition left, Condition right) {
        this(List.of(left, right));
    }

    @Override
    public <R> R accept(ConditionVisitor<R> visitor) {
        return visitor.visit(this);
    }
}

