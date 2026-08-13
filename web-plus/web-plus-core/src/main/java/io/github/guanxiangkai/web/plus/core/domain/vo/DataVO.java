package io.github.guanxiangkai.web.plus.core.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 数据详情视图对象（对应 {@link io.github.guanxiangkai.web.plus.core.entity.DataEntity}）
 * <p>
 * 在 {@link BaseVO} 基础上增加 {@code remark}、{@code enabled}、{@code sortOrder}。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Data
 * @EqualsAndHashCode(callSuper = true)
 * @Schema(description = "职位详情VO")
 * public class PostVO extends DataVO {
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
public abstract class DataVO extends BaseVO {

    @Serial
    private static final long serialVersionUID = 1L;

    private String remark;

    private Boolean enabled;

    private Integer sortOrder;
}

