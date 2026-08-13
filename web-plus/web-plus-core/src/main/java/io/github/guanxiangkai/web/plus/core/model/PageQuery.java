package io.github.guanxiangkai.web.plus.core.model;

/**
 * 分页查询协议接口
 * <p>
 * 所有需要支持分页的查询 DTO 实现此接口，
 * {@code BaseServiceImpl} 依赖此接口读取分页参数。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class PostPageDTO extends PageRequest {
 *     private String title;       // 业务过滤字段
 *     private String status;
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface PageQuery {

    /**
     * 当前页码（1-based）
     */
    int page();

    /**
     * 每页条数
     */
    int size();

    /**
     * 排序字段名（可空）
     */
    String sortBy();

    /**
     * 排序方向：{@code ASC} / {@code DESC}（默认 DESC）
     */
    String sortDir();

    /**
     * 是否升序
     */
    default boolean isAsc() {
        return "ASC".equalsIgnoreCase(sortDir());
    }
}

