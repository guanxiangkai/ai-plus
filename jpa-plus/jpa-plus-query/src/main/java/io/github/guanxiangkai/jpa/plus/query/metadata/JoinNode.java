package io.github.guanxiangkai.jpa.plus.query.metadata;

/**
 * Join 节点
 *
 * @param table     目标表元数据
 * @param type      Join 类型
 * @param condition Join 条件
 */
public record JoinNode(TableMeta table, JoinType type, JoinCondition condition) {
}

