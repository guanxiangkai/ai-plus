package io.github.guanxiangkai.jpa.plus.query.pagination;

import java.util.Map;

/**
 * Keyset 分页游标（不可变值对象）
 *
 * <p>Keyset Pagination（也称 "Seek Method"）通过记录上一页最后一行的排序字段值，
 * 在下一页查询时转换为 {@code WHERE} 条件，避免传统 OFFSET 分页在大偏移量下的全表扫描问题。</p>
 *
 * <h3>典型场景</h3>
 * <ul>
 *   <li>时间线类查询（如按 {@code created_time DESC} 翻页）</li>
 *   <li>深分页（第 1000 页以后，OFFSET 分页性能急剧下降）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 首页（无游标）
 * KeysetPageResult<Order> page1 = executor.pageKeyset(
 *         QueryWrapper.from(Order.class).orderByDesc(Order::getCreatedTime),
 *         KeysetCursor.first(20));
 *
 * // 下一页（使用上一页返回的游标）
 * if (page1.hasNext()) {
 *     KeysetPageResult<Order> page2 = executor.pageKeyset(wrapper, page1.nextCursor());
 * }
 * }</pre>
 *
 * @param lastValues 上一页最后一行的排序字段快照（字段名 → 值），首页时为空 {@code Map}
 * @param pageSize   每页数量（必须 > 0）
 * @author guanxiangkai
 * @since 1.0.0
 */
public record KeysetCursor(
        Map<String, Object> lastValues,
        int pageSize
) {

    public KeysetCursor {
        if (pageSize <= 0) throw new IllegalArgumentException("pageSize must be > 0");
        lastValues = lastValues == null ? Map.of() : Map.copyOf(lastValues);
    }

    /**
     * 创建首页游标（没有上一页）
     *
     * @param pageSize 每页数量
     */
    public static KeysetCursor first(int pageSize) {
        return new KeysetCursor(Map.of(), pageSize);
    }

    /**
     * 是否为首页（无前驱游标值）
     */
    public boolean isFirst() {
        return lastValues.isEmpty();
    }
}

