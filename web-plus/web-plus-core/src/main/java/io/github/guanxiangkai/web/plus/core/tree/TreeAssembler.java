package io.github.guanxiangkai.web.plus.core.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/**
 * 将平面节点集合装配为森林的工具。
 * <p>
 * 装配会先完成 ID、重复、环和父级关系校验，再一次性写入子节点，因此校验失败不会改动任何节点的
 * {@code children} 属性。根节点由 {@code rootParent} 判断；父级不在当前集合中的孤儿节点不会提升为根。
 * </p>
 */
public final class TreeAssembler {

    private TreeAssembler() {
    }

    /**
     * 使用方法引用从普通 DTO 装配森林。
     *
     * @param nodes 节点集合
     * @param id 获取节点 ID 的函数；每个 ID 必须非空且唯一
     * @param parentId 获取父级 ID 的函数；根节点可返回 {@code null}
     * @param children 写入直接子节点的函数
     * @param rootParent 判断父级 ID 是否属于本次装配根的谓词
     * @param comparator 节点排序器；为 {@code null} 时保留输入顺序
     * @param <ID> 身份标识类型
     * @param <N> 节点类型
     * @return 已装配的根节点列表
     * @throws IllegalArgumentException ID 为空、重复 ID 或存在父级环时抛出
     */
    public static <ID, N> List<N> assemble(
            List<N> nodes,
            Function<? super N, ? extends @Nullable ID> id,
            Function<? super N, ? extends @Nullable ID> parentId,
            BiConsumer<? super N, List<N>> children,
            Predicate<? super @Nullable ID> rootParent,
            @Nullable Comparator<? super N> comparator
    ) {
        Objects.requireNonNull(nodes, "nodes 不能为空");
        Objects.requireNonNull(id, "id 不能为空");
        Objects.requireNonNull(parentId, "parentId 不能为空");
        Objects.requireNonNull(children, "children 不能为空");
        Objects.requireNonNull(rootParent, "rootParent 不能为空");

        Map<ID, NodeEntry<ID, N>> entriesById = new LinkedHashMap<>(nodes.size());
        for (N node : nodes) {
            N actualNode = Objects.requireNonNull(node, "nodes 不能包含 null 节点");
            ID nodeId = requireNodeId(id.apply(actualNode));
            NodeEntry<ID, N> entry = new NodeEntry<>(actualNode, parentId.apply(actualNode));
            if (entriesById.putIfAbsent(nodeId, entry) != null) {
                throw new IllegalArgumentException("树节点 ID 重复: " + nodeId);
            }
        }

        rejectCycles(entriesById);

        Map<ID, List<N>> childrenByParentId = new LinkedHashMap<>();
        for (Map.Entry<ID, NodeEntry<ID, N>> entry : entriesById.entrySet()) {
            @Nullable ID nodeParentId = entry.getValue().parentId();
            if (nodeParentId != null && entriesById.containsKey(nodeParentId)) {
                childrenByParentId.computeIfAbsent(nodeParentId, ignored -> new ArrayList<>())
                        .add(entry.getValue().node());
            }
        }

        List<N> roots = new ArrayList<>();
        for (Map.Entry<ID, NodeEntry<ID, N>> entry : entriesById.entrySet()) {
            if (rootParent.test(entry.getValue().parentId())) {
                roots.add(entry.getValue().node());
            }
        }

        if (comparator != null) {
            roots.sort(comparator);
            childrenByParentId.values().forEach(nodeChildren -> nodeChildren.sort(comparator));
        }

        for (Map.Entry<ID, NodeEntry<ID, N>> entry : entriesById.entrySet()) {
            List<N> nodeChildren = childrenByParentId.get(entry.getKey());
            children.accept(entry.getValue().node(), nodeChildren == null ? List.of() : List.copyOf(nodeChildren));
        }
        return List.copyOf(roots);
    }

    /**
     * 使用方法引用从普通 DTO 装配森林，并保留输入顺序。
     *
     * @param nodes 节点集合
     * @param id 获取节点 ID 的函数；每个 ID 必须非空且唯一
     * @param parentId 获取父级 ID 的函数；根节点可返回 {@code null}
     * @param children 写入直接子节点的函数
     * @param rootParent 判断父级 ID 是否属于本次装配根的谓词
     * @param <ID> 身份标识类型
     * @param <N> 节点类型
     * @return 已装配的根节点列表
     */
    public static <ID, N> List<N> assemble(
            List<N> nodes,
            Function<? super N, ? extends @Nullable ID> id,
            Function<? super N, ? extends @Nullable ID> parentId,
            BiConsumer<? super N, List<N>> children,
            Predicate<? super @Nullable ID> rootParent
    ) {
        return assemble(nodes, id, parentId, children, rootParent, null);
    }

    /**
     * 使用 {@link TreeNode} 契约装配森林。
     *
     * @param nodes 节点集合
     * @param rootParent 判断父级 ID 是否属于本次装配根的谓词
     * @param comparator 节点排序器；为 {@code null} 时保留输入顺序
     * @param <ID> 身份标识类型
     * @param <N> 节点类型
     * @return 已装配的根节点列表
     */
    public static <ID, N extends TreeNode<ID, N>> List<N> assemble(
            List<N> nodes,
            Predicate<? super @Nullable ID> rootParent,
            @Nullable Comparator<? super N> comparator
    ) {
        return assemble(nodes, TreeNode::getId, TreeNode::getParentId, TreeNode::setChildren, rootParent, comparator);
    }

    /**
     * 使用 {@link TreeNode} 契约装配森林，并保留输入顺序。
     *
     * @param nodes 节点集合
     * @param rootParent 判断父级 ID 是否属于本次装配根的谓词
     * @param <ID> 身份标识类型
     * @param <N> 节点类型
     * @return 已装配的根节点列表
     */
    public static <ID, N extends TreeNode<ID, N>> List<N> assemble(
            List<N> nodes,
            Predicate<? super @Nullable ID> rootParent
    ) {
        return assemble(nodes, rootParent, null);
    }

    private static <ID, N> void rejectCycles(Map<ID, NodeEntry<ID, N>> entriesById) {
        Map<ID, VisitState> states = new LinkedHashMap<>(entriesById.size());
        for (ID startId : entriesById.keySet()) {
            if (states.containsKey(startId)) {
                continue;
            }
            List<ID> path = new ArrayList<>();
            @Nullable ID currentId = startId;
            while (currentId != null) {
                ID existingId = Objects.requireNonNull(currentId);
                NodeEntry<ID, N> entry = entriesById.get(existingId);
                if (entry == null) {
                    break;
                }
                VisitState state = states.get(existingId);
                if (state == VisitState.VISITING) {
                    throw new IllegalArgumentException("树节点存在父级环: " + existingId);
                }
                if (state == VisitState.VISITED) {
                    break;
                }
                states.put(existingId, VisitState.VISITING);
                path.add(existingId);
                currentId = entry.parentId();
            }
            path.forEach(nodeId -> states.put(nodeId, VisitState.VISITED));
        }
    }

    private enum VisitState {
        VISITING,
        VISITED
    }

    private static <ID> ID requireNodeId(@Nullable ID nodeId) {
        if (nodeId == null || nodeId instanceof String stringId && stringId.isBlank()) {
            throw new IllegalArgumentException("树节点 ID 不能为空或空白");
        }
        return Objects.requireNonNull(nodeId);
    }

    private record NodeEntry<ID, N>(N node, @Nullable ID parentId) {
    }
}
