package io.github.guanxiangkai.jpa.plus.query.normalizer;

import io.github.guanxiangkai.jpa.plus.query.ast.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 条件规范化器
 * <p>
 * 通过 Visitor 模式遍历条件树，执行以下优化：
 * - And/Or 扁平化（嵌套的 And(And(...)) → 单层 And(...)）
 * - 去重（基于 toString 比较）
 * - 空条件消除
 */
public class ConditionNormalizer implements ConditionVisitor<Condition> {

    @Override
    public Condition visit(And and) {
        List<Condition> flattened = new ArrayList<>();
        for (Condition child : and.conditions()) {
            Condition norm = child.accept(this);
            if (norm == null) continue;
            if (norm instanceof And(List<Condition> conditions)) {
                flattened.addAll(conditions);
            } else {
                flattened.add(norm);
            }
        }
        flattened = deduplicate(flattened);
        if (flattened.isEmpty()) return null;
        if (flattened.size() == 1) return flattened.getFirst();
        return new And(flattened);
    }

    @Override
    public Condition visit(Or or) {
        List<Condition> flattened = new ArrayList<>();
        for (Condition child : or.conditions()) {
            Condition norm = child.accept(this);
            if (norm == null) continue;
            if (norm instanceof Or(List<Condition> conditions)) {
                flattened.addAll(conditions);
            } else {
                flattened.add(norm);
            }
        }
        flattened = deduplicate(flattened);
        if (flattened.isEmpty()) return null;
        if (flattened.size() == 1) return flattened.getFirst();
        return new Or(flattened);
    }

    @Override
    public Condition visit(Not not) {
        Condition inner = not.condition().accept(this);
        if (inner == null) return null;
        // 双重否定消除
        if (inner instanceof Not(Condition condition)) {
            return condition;
        }
        return new Not(inner);
    }

    @Override
    public Condition visit(Eq eq) {
        return eq;
    }

    @Override
    public Condition visit(Ne ne) {
        return ne;
    }

    @Override
    public Condition visit(Gt gt) {
        return gt;
    }

    @Override
    public Condition visit(Ge ge) {
        return ge;
    }

    @Override
    public Condition visit(Lt lt) {
        return lt;
    }

    @Override
    public Condition visit(Le le) {
        return le;
    }

    @Override
    public Condition visit(Like like) {
        return like;
    }

    @Override
    public Condition visit(In in) {
        if (in.values() == null || in.values().isEmpty()) return null;
        return in;
    }

    @Override
    public Condition visit(Between between) {
        return between;
    }

    @Override
    public Condition visit(Exists exists) {
        return exists;
    }

    @Override
    public Condition visit(SubQuery subQuery) {
        return subQuery;
    }

    @Override
    public Condition visit(AggregateCondition aggregateCondition) {
        return aggregateCondition;
    }

    /**
     * 基于 toString 的简化去重
     */
    private List<Condition> deduplicate(List<Condition> conditions) {
        Set<String> seen = new LinkedHashSet<>();
        List<Condition> result = new ArrayList<>();
        for (Condition c : conditions) {
            String key = c.toString();
            if (seen.add(key)) {
                result.add(c);
            }
        }
        return result;
    }
}

