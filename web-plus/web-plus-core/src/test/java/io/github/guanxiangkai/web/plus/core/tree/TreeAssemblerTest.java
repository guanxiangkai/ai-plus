package io.github.guanxiangkai.web.plus.core.tree;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TreeAssemblerTest {

    @Test
    void assemblesOrdinaryDtosWithLongIdentifiers() {
        FlatNode root = new FlatNode(1L, null, "root");
        FlatNode child = new FlatNode(2L, 1L, "child");
        FlatNode leaf = new FlatNode(3L, 2L, "leaf");

        List<FlatNode> roots = TreeAssembler.assemble(
                List.of(root, child, leaf), FlatNode::id, FlatNode::parentId, FlatNode::setChildren,
                parentId -> parentId == null, null
        );

        assertThat(roots).containsExactly(root);
        assertThat(root.children()).containsExactly(child);
        assertThat(child.children()).containsExactly(leaf);
        assertThat(leaf.children()).isEmpty();
    }

    @Test
    void keepsInputOrderForEqualSortKeysAndExcludesOrphans() {
        FlatNode second = new FlatNode(2L, 0L, "second");
        FlatNode first = new FlatNode(1L, 0L, "first");
        FlatNode orphan = new FlatNode(3L, 99L, "orphan");

        List<FlatNode> roots = TreeAssembler.assemble(
                List.of(second, first, orphan), FlatNode::id, FlatNode::parentId, FlatNode::setChildren,
                parentId -> parentId != null && parentId == 0L, Comparator.comparing(FlatNode::sortKey)
        );

        assertThat(roots).containsExactly(second, first);
        assertThat(orphan.children()).isEmpty();
    }

    @Test
    void assemblesOnlyTheRequestedParentSubtree() {
        TreeTestNode root = new TreeTestNode("root", null);
        TreeTestNode subtree = new TreeTestNode("subtree", "root");
        TreeTestNode child = new TreeTestNode("child", "subtree");
        TreeTestNode unrelated = new TreeTestNode("unrelated", null);

        List<TreeTestNode> roots = TreeAssembler.assemble(
                List.of(root, subtree, child, unrelated), parentId -> "root".equals(parentId), null
        );

        assertThat(roots).containsExactly(subtree);
        assertThat(subtree.children).containsExactly(child);
        assertThat(root.children).containsExactly(subtree);
        assertThat(unrelated.children).isEmpty();
    }

    @Test
    void rejectsInvalidIdentifiersAndCyclesBeforeChangingChildren() {
        FlatNode preserved = new FlatNode(1L, null, "preserved");
        preserved.setChildren(List.of(new FlatNode(9L, null, "existing")));
        FlatNode duplicate = new FlatNode(1L, null, "duplicate");

        assertThatThrownBy(() -> assemble(List.of(preserved, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");
        assertThat(preserved.children()).extracting(FlatNode::name).containsExactly("existing");

        FlatNode self = new FlatNode(2L, 2L, "self");
        self.setChildren(List.of(new FlatNode(8L, null, "old-self-child")));
        FlatNode cycleA = new FlatNode(3L, 4L, "a");
        FlatNode cycleB = new FlatNode(4L, 3L, "b");
        assertThatThrownBy(() -> assemble(List.of(self))).isInstanceOf(IllegalArgumentException.class);
        assertThat(self.children()).extracting(FlatNode::name).containsExactly("old-self-child");
        assertThatThrownBy(() -> assemble(List.of(cycleA, cycleB))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assemble(List.of(new FlatNode(null, null, "missing"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
        assertThatThrownBy(() -> TreeAssembler.assemble(
                List.of(new TreeTestNode("   ", null)), TreeTestNode::getId, TreeTestNode::getParentId,
                TreeTestNode::setChildren, parentId -> parentId == null, null
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("空白");
    }

    @Test
    void handlesDeepChainsWithoutRecursion() {
        List<FlatNode> nodes = new ArrayList<>();
        for (long index = 0; index < 10_000; index++) {
            nodes.add(new FlatNode(index, index == 0 ? null : index - 1, "node-" + index));
        }

        List<FlatNode> roots = assemble(nodes);

        assertThat(roots).hasSize(1);
        FlatNode current = roots.getFirst();
        for (int index = 1; index < 10_000; index++) {
            assertThat(current.children()).hasSize(1);
            current = current.children().getFirst();
        }
        assertThat(current.children()).isEmpty();
    }

    private static List<FlatNode> assemble(List<FlatNode> nodes) {
        return TreeAssembler.assemble(
                nodes, FlatNode::id, FlatNode::parentId, FlatNode::setChildren, parentId -> parentId == null, null
        );
    }

    private static final class FlatNode {
        private final Long id;
        private final Long parentId;
        private final String name;
        private List<FlatNode> children = List.of();

        private FlatNode(Long id, Long parentId, String name) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
        }

        private Long id() {
            return id;
        }

        private Long parentId() {
            return parentId;
        }

        private String name() {
            return name;
        }

        private String sortKey() {
            return "same";
        }

        private List<FlatNode> children() {
            return children;
        }

        private void setChildren(List<FlatNode> children) {
            this.children = children;
        }
    }

    private static final class TreeTestNode implements TreeNode<String, TreeTestNode> {
        private final String id;
        private final String parentId;
        private List<TreeTestNode> children = List.of();

        private TreeTestNode(String id, String parentId) {
            this.id = id;
            this.parentId = parentId;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getParentId() {
            return parentId;
        }

        @Override
        public List<TreeTestNode> getChildren() {
            return children;
        }

        @Override
        public void setChildren(List<TreeTestNode> children) {
            this.children = children;
        }
    }
}
