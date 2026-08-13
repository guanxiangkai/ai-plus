package io.github.guanxiangkai.jpa.plus.query.wrapper;

import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;
import io.github.guanxiangkai.jpa.plus.query.context.QueryMetadata;

/**
 * 默认 DeleteWrapper 实现
 */
public class DefaultDeleteWrapper<T> extends AbstractWrapper<T> implements DeleteWrapper<T> {

    private boolean fullTableMutationAllowed = false;

    public DefaultDeleteWrapper(Class<T> entityClass) {
        super(entityClass);
    }

    private DefaultDeleteWrapper(DefaultDeleteWrapper<T> source, int offset, int rows) {
        super(source, offset, rows);
        this.fullTableMutationAllowed = source.fullTableMutationAllowed;
    }

    @Override
    public DeleteWrapper<T> allowFullTableMutation() {
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
        return new DefaultDeleteWrapper<>(this, offset, rows);
    }

    @Override
    public QueryContext buildContext() {
        QueryMetadata metadata = QueryMetadata.delete(tableMeta);
        return new QueryContext(metadata, buildRuntime());
    }
}

