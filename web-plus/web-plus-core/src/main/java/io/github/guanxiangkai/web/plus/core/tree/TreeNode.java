package io.github.guanxiangkai.web.plus.core.tree;

import io.github.guanxiangkai.web.plus.core.model.Identifiable;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 同时具备身份、父级关系和子节点容器的强类型树节点。
 *
 * @param <ID> 身份标识类型
 * @param <N> 节点自身类型
 */
public interface TreeNode<ID, N extends TreeNode<ID, N>> extends Identifiable<ID>, ParentAware<ID> {

    /**
     * 获取直接子节点。
     *
     * @return 直接子节点列表；装配前实现可以返回 {@code null}，树装配完成后叶子节点返回空列表
     */
    @Nullable List<N> getChildren();

    /**
     * 设置直接子节点。
     *
     * @param children 已装配的直接子节点列表
     */
    void setChildren(List<N> children);
}
