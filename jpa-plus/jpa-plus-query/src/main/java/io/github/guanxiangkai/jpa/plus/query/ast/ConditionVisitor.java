package io.github.guanxiangkai.jpa.plus.query.ast;

/**
 * 条件表达式访问者接口（Visitor 模式）
 *
 * @param <R> 访问结果类型
 */
public interface ConditionVisitor<R> {

    R visit(Eq eq);

    R visit(Ne ne);

    R visit(Gt gt);

    R visit(Ge ge);

    R visit(Lt lt);

    R visit(Le le);

    R visit(Like like);

    R visit(In in);

    R visit(Between between);

    R visit(And and);

    R visit(Or or);

    R visit(Not not);

    R visit(Exists exists);

    R visit(SubQuery subQuery);

    /**
     * 访问聚合条件（HAVING 子句使用）
     */
    R visit(AggregateCondition aggregateCondition);
}

