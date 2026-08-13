package io.github.guanxiangkai.jpa.plus.query.ast;

import java.util.List;

/**
 * OR 组合条件：condition1 OR condition2 OR ...
 */
public record Or(List<Condition> conditions) implements Condition {

    public Or(Condition left, Condition right) {
        this(List.of(left, right));
    }

    @Override
    public <R> R accept(ConditionVisitor<R> visitor) {
        return visitor.visit(this);
    }
}

