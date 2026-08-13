package io.github.guanxiangkai.jpa.plus.query.metadata;

import io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 连接图 —— 管理多表 Join 关系
 * <p>
 * 限制最大 Join 深度，防止笛卡尔积爆炸。
 */
public class JoinGraph {

    private final TableMeta root;
    private final List<JoinNode> nodes = new ArrayList<>();
    private final int maxDepth;

    public JoinGraph(TableMeta root, int maxDepth) {
        this.root = root;
        this.maxDepth = maxDepth;
    }

    public JoinGraph(TableMeta root) {
        this(root, 3); // 默认最大深度 3
    }

    /**
     * 添加 Join 节点
     *
     * @throws JpaPlusException 超过最大深度时抛出
     */
    public void addJoin(JoinNode node) {
        if (nodes.size() >= maxDepth) {
            throw new JpaPlusException("Join depth exceeds limit: " + maxDepth);
        }
        nodes.add(node);
    }

    public TableMeta root() {
        return root;
    }

    public List<JoinNode> nodes() {
        return Collections.unmodifiableList(nodes);
    }

    public int maxDepth() {
        return maxDepth;
    }

    public boolean hasJoins() {
        return !nodes.isEmpty();
    }
}

