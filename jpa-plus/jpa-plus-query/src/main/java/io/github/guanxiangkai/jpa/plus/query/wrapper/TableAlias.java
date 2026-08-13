package io.github.guanxiangkai.jpa.plus.query.wrapper;

import io.github.guanxiangkai.jpa.plus.query.metadata.ColumnMeta;
import io.github.guanxiangkai.jpa.plus.query.metadata.TableMeta;

/**
 * 表别名包装器
 * <p>
 * 用于 JoinWrapper 中给表分配别名，并通过 col() 获取带别名的列引用。
 */
public record TableAlias<T>(TableMeta meta) {

    /**
     * 获取列引用
     */
    public ColumnAlias<?> col(TypedGetter<T, ?> column) {
        String columnName = LambdaColumnResolver.resolve(column);
        return new ColumnAlias<>(ColumnMeta.of(meta, columnName, null), null);
    }
}

