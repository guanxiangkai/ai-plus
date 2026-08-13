package io.github.guanxiangkai.web.plus.core.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 数据分页列表视图对象（对应 {@link io.github.guanxiangkai.web.plus.core.entity.DataEntity}）
 * <p>
 * 在 {@link BasePageVO} 基础上增加 {@code enabled} 和 {@code sortOrder}。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Data
 * @EqualsAndHashCode(callSuper = true)
 * // 可按需在子类上加 @Schema
 * public class PostPageVO extends DataPageVO {
 *     private String positionName;
 *     private String positionCode;
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class DataPageVO extends BasePageVO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Boolean enabled;

    private Integer sortOrder;
}

