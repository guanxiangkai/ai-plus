package io.github.guanxiangkai.jpa.plus.query.wrapper;

import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;
import io.github.guanxiangkai.jpa.plus.query.context.QueryMetadata;

/**
 * 默认 QueryWrapper 实现
 */
public class DefaultQueryWrapper<T> extends AbstractWrapper<T> {

    public DefaultQueryWrapper(Class<T> entityClass) {
        super(entityClass);
    }

    /**
     * copy-on-write 构造器（由 AbstractWrapper 拷贝构造器调用链支持）
     */
    private DefaultQueryWrapper(AbstractWrapper<T> source, int offset, int rows) {
        super(source, offset, rows);
    }

    /**
     * 重写 limit：返回携带新分页参数的副本，不修改当前实例。
     *
     * <p>这是一种 copy-on-write 语义：
     * {@code one(wrapper)} 内部调用 {@code wrapper.limit(0, 1)} 时，
     * 返回的是新对象，原始 {@code wrapper} 保持不变，可安全复用。</p>
     */
    @Override
    public QueryWrapper<T> limit(int offset, int rows) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0, got: " + offset);
        }
        if (rows <= 0) {
            throw new IllegalArgumentException("rows must be > 0, got: " + rows);
        }
        return new DefaultQueryWrapper<>(this, offset, rows);
    }

    @Override
    public QueryContext buildContext() {
        QueryMetadata metadata = QueryMetadata.select(tableMeta);
        return new QueryContext(metadata, buildRuntime());
    }
}

