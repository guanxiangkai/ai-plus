package io.github.guanxiangkai.web.plus.core.model;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

import java.util.List;

/**
 * 统一分页响应体
 *
 * @param <T> 数据类型
 * @author guanxiangkai
 * @since 1.0.0
 */
@RegisterReflectionForBinding
public record PageResponse<T>(
        List<T> records,
        long total,
        int pageNum,
        int pageSize,
        int pages
) {
    public PageResponse {
        records = records != null ? List.copyOf(records) : List.of();
        if (pages <= 0 && total > 0 && pageSize > 0) {
            pages = (int) Math.ceil((double) total / pageSize);
        }
    }

    // ── 静态工厂 ─────────────────────────────────────────────────

    public static <T> PageResponse<T> of(List<T> records, long total, int pageNum, int pageSize) {
        int pages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
        return new PageResponse<>(records, total, pageNum, pageSize, pages);
    }

    public static <T> PageResponse<T> empty() {
        return new PageResponse<>(List.of(), 0, 1, 10, 0);
    }

    // ── 工具方法 ─────────────────────────────────────────────────

    public boolean isEmpty() {
        return records.isEmpty();
    }

    public boolean hasNext() {
        return pageNum < pages;
    }

    public boolean hasPrevious() {
        return pageNum > 1;
    }

    public boolean isFirst() {
        return pageNum == 1;
    }

    public boolean isLast() {
        return pageNum >= pages;
    }
}

