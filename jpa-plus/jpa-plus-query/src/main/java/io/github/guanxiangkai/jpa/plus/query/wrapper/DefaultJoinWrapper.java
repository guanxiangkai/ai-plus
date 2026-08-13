package io.github.guanxiangkai.jpa.plus.query.wrapper;

import io.github.guanxiangkai.jpa.plus.query.ast.*;
import io.github.guanxiangkai.jpa.plus.query.context.*;
import io.github.guanxiangkai.jpa.plus.query.metadata.*;

import java.util.*;

/**
 * 默认 JoinWrapper 实现
 */
public class DefaultJoinWrapper<T> implements JoinWrapper<T> {

    private final Class<T> entityClass;
    private final TableMeta rootTable;
    private final JoinGraph joinGraph;
    private final List<SelectColumn> selects = new ArrayList<>();
    private final List<Condition> conditions = new ArrayList<>();
    private final List<OrderBy> orderBys = new ArrayList<>();
    private Integer offset;
    private Integer rows;

    public DefaultJoinWrapper(Class<T> entityClass) {
        this.entityClass = entityClass;
        this.rootTable = TableMeta.of(entityClass);
        this.joinGraph = new JoinGraph(rootTable);
    }

    /**
     * Copy-on-write 构造器，仅由 {@link #limit} 使用。
     * 复制所有字段；joinGraph 深度拷贝以避免两份 wrapper 共享可变节点列表。
     */
    private DefaultJoinWrapper(DefaultJoinWrapper<T> source) {
        this.entityClass = source.entityClass;
        this.rootTable = source.rootTable;
        // P1-35: Deep-copy JoinGraph so that mutations (addJoin) on the copy do not affect the original.
        JoinGraph srcGraph = source.joinGraph;
        JoinGraph newGraph = new JoinGraph(srcGraph.root(), srcGraph.maxDepth());
        srcGraph.nodes().forEach(newGraph::addJoin);
        this.joinGraph = newGraph;
        this.selects.addAll(source.selects);
        this.conditions.addAll(source.conditions);
        this.orderBys.addAll(source.orderBys);
        this.offset = source.offset;
        this.rows = source.rows;
    }

    @Override
    public <J> TableAlias<J> as(Class<J> entityClass, String alias) {
        TableMeta meta = TableMeta.of(entityClass, alias);
        return new TableAlias<>(meta);
    }

    @Override
    public <J> JoinWrapper<T> leftJoin(TableAlias<J> right, ColumnAlias<?> leftCol, ColumnAlias<?> rightCol) {
        return addJoin(right, leftCol, rightCol, JoinType.LEFT);
    }

    @Override
    public <J> JoinWrapper<T> innerJoin(TableAlias<J> right, ColumnAlias<?> leftCol, ColumnAlias<?> rightCol) {
        return addJoin(right, leftCol, rightCol, JoinType.INNER);
    }

    @Override
    public <J> JoinWrapper<T> rightJoin(TableAlias<J> right, ColumnAlias<?> leftCol, ColumnAlias<?> rightCol) {
        return addJoin(right, leftCol, rightCol, JoinType.RIGHT);
    }

    private <J> JoinWrapper<T> addJoin(TableAlias<J> right, ColumnAlias<?> leftCol, ColumnAlias<?> rightCol, JoinType type) {
        JoinCondition condition = new JoinCondition(leftCol.meta(), rightCol.meta());
        JoinNode node = new JoinNode(right.meta(), type, condition);
        joinGraph.addJoin(node);
        return this;
    }

    @Override
    public JoinWrapper<T> select(SelectColumn... columns) {
        selects.addAll(Arrays.asList(columns));
        return this;
    }

    @Override
    public JoinWrapper<T> eq(ColumnAlias<?> column, Object value) {
        conditions.add(new Eq(column.meta(), value));
        return this;
    }

    @Override
    public JoinWrapper<T> ne(ColumnAlias<?> column, Object value) {
        conditions.add(new Ne(column.meta(), value));
        return this;
    }

    @Override
    public JoinWrapper<T> like(ColumnAlias<?> column, String value) {
        conditions.add(new Like(column.meta(), value, LikeMode.ANYWHERE));
        return this;
    }

    @Override
    public JoinWrapper<T> in(ColumnAlias<?> column, Collection<?> values) {
        conditions.add(new In(column.meta(), values));
        return this;
    }

    @Override
    public JoinWrapper<T> condition(Condition condition) {
        if (condition != null) {
            conditions.add(condition);
        }
        return this;
    }

    @Override
    public JoinWrapper<T> orderByAsc(ColumnAlias<?> column) {
        orderBys.add(new OrderBy(column.meta(), OrderBy.Direction.ASC));
        return this;
    }

    @Override
    public JoinWrapper<T> orderByDesc(ColumnAlias<?> column) {
        orderBys.add(new OrderBy(column.meta(), OrderBy.Direction.DESC));
        return this;
    }

    @Override
    public JoinWrapper<T> limit(int offset, int rows) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0, got: " + offset);
        }
        if (rows <= 0) {
            throw new IllegalArgumentException("rows must be > 0, got: " + rows);
        }
        DefaultJoinWrapper<T> copy = new DefaultJoinWrapper<>(this);
        copy.offset = offset;
        copy.rows = rows;
        return copy;
    }

    @Override
    public JoinGraph buildJoinGraph() {
        return joinGraph;
    }

    @Override
    public QueryContext buildContext() {
        Condition where = buildCondition();
        QueryMetadata metadata = new QueryMetadata(
                rootTable,
                joinGraph,
                List.copyOf(selects),
                QueryType.SELECT,
                List.of()
        );
        QueryRuntime runtime = new QueryRuntime(
                where,
                List.copyOf(orderBys),
                Map.of(),
                offset,
                rows,
                List.of(),
                null
        );
        return new QueryContext(metadata, runtime);
    }

    private Condition buildCondition() {
        if (conditions.isEmpty()) return null;
        if (conditions.size() == 1) return conditions.getFirst();
        return new And(List.copyOf(conditions));
    }
}
