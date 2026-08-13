package io.github.guanxiangkai.jpa.plus.query.context;

import io.github.guanxiangkai.jpa.plus.query.metadata.ColumnMeta;

/**
 * 排序
 *
 * @param column    排序列
 * @param direction 排序方向
 */
public record OrderBy(ColumnMeta column, Direction direction) {

    public enum Direction {
        ASC, DESC
    }
}

