package io.github.guanxiangkai.jpa.plus.query.wrapper;

import io.github.guanxiangkai.jpa.plus.query.context.Assignment;
import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;
import io.github.guanxiangkai.jpa.plus.query.context.QueryMetadata;
import io.github.guanxiangkai.jpa.plus.query.context.QueryType;
import io.github.guanxiangkai.jpa.plus.query.metadata.ColumnMeta;
import io.github.guanxiangkai.jpa.plus.query.metadata.JoinGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认 UpdateWrapper 实现
 */
public class DefaultUpdateWrapper<T> extends AbstractWrapper<T> implements UpdateWrapper<T> {

    private final List<Assignment> assignments = new ArrayList<>();
    private boolean fullTableMutationAllowed = false;

    public DefaultUpdateWrapper(Class<T> entityClass) {
        super(entityClass);
    }

    private DefaultUpdateWrapper(DefaultUpdateWrapper<T> source, int offset, int rows) {
        super(source, offset, rows);
        this.assignments.addAll(source.assignments);
        this.fullTableMutationAllowed = source.fullTableMutationAllowed;
    }

    @Override
    public UpdateWrapper<T> set(TypedGetter<T, ?> column, Object value) {
        ColumnMeta col = resolveColumn(column);
        assignments.add(new Assignment(col, value));
        return this;
    }

    @Override
    public UpdateWrapper<T> setNull(TypedGetter<T, ?> column) {
        ColumnMeta col = resolveColumn(column);
        assignments.add(new Assignment(col, null));
        return this;
    }

    @Override
    public UpdateWrapper<T> allowFullTableMutation() {
        this.fullTableMutationAllowed = true;
        return this;
    }

    public boolean isFullTableMutationAllowed() {
        return fullTableMutationAllowed;
    }

    @Override
    public QueryWrapper<T> limit(int offset, int rows) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0, got: " + offset);
        }
        if (rows <= 0) {
            throw new IllegalArgumentException("rows must be > 0, got: " + rows);
        }
        return new DefaultUpdateWrapper<>(this, offset, rows);
    }

    @Override
    public QueryContext buildContext() {
        QueryMetadata metadata = new QueryMetadata(
                tableMeta,
                new JoinGraph(tableMeta),
                List.of(),
                QueryType.UPDATE,
                List.copyOf(assignments)
        );
        return new QueryContext(metadata, buildRuntime());
    }
}

