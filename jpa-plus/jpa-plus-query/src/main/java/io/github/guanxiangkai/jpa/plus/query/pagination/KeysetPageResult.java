package io.github.guanxiangkai.jpa.plus.query.pagination;

import java.util.List;

/**
 * Keyset 分页结果（不可变值对象）
 *
 * <p>封装 Keyset Pagination 查询的结果，包含当前页数据、下一页游标及是否有下一页的标记。</p>
 *
 * @param content    当前页数据列表（大小 ≤ {@code cursor.pageSize()}）
 * @param nextCursor 下一页游标（当 {@link #hasNext()} 为 {@code true} 时有效）
 * @param hasNext    是否存在下一页
 * @param <T>        数据类型
 * @author guanxiangkai
 * @since 1.0.0
 */
public record KeysetPageResult<T>(
        List<T> content,
        KeysetCursor nextCursor,
        boolean hasNext
) {

    public KeysetPageResult {
        content = content == null ? List.of() : List.copyOf(content);
    }

    /**
     * 创建空结果（无下一页）
     *
     * @param pageSize 当前请求的页大小（用于构造空游标）
     */
    public static <T> KeysetPageResult<T> empty(int pageSize) {
        return new KeysetPageResult<>(List.of(), KeysetCursor.first(pageSize), false);
    }
}

