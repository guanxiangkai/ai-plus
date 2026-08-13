package io.github.guanxiangkai.jpa.plus.query.ast;

/**
 * 条件表达式 AST 根接口（密封接口）
 *
 * <p>所有查询条件都用此密封接口（sealed interface）表示。
 * 条件分为三类：
 * <ul>
 *   <li><b>原子条件：</b>{@link Eq}、{@link Ne}、{@link Gt}、{@link Ge}、{@link Lt}、{@link Le}、
 *       {@link Like}、{@link In}、{@link Between}</li>
 *   <li><b>复合条件：</b>{@link And}、{@link Or}、{@link Not}</li>
 *   <li><b>子查询：</b>{@link Exists}、{@link SubQuery}</li>
 * </ul>
 * </p>
 *
 * <p>通过 {@link #accept(ConditionVisitor)} 支持访问者模式，
 * SQL 编译器、条件规范化器等均通过 Visitor 遍历 AST。</p>
 *
 * <p><b>设计模式：</b>
 * <ul>
 *   <li>组合模式（Composite） —— And/Or 组合多个子条件</li>
 *   <li>访问者模式（Visitor） —— 通过 {@link ConditionVisitor} 遍历 AST</li>
 *   <li>密封类型（Sealed Types） —— Java 17+ 特性，穷举所有条件类型</li>
 * </ul>
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public sealed interface Condition permits Eq, Ne, Gt, Ge, Lt, Le, Like, In, Between, And, Or, Not, Exists, SubQuery, AggregateCondition {

    /**
     * 接受访问者
     */
    <R> R accept(ConditionVisitor<R> visitor);
}
