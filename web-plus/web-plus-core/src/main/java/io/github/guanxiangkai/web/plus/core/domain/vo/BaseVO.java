package io.github.guanxiangkai.web.plus.core.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础详情视图对象（对应 {@link io.github.guanxiangkai.web.plus.core.entity.BaseEntity}）
 * <p>
 * 包含所有详情 VO 的公共字段：主键、创建/更新时间。
 * </p>
 *
 * <h3>继承关系</h3>
 * <pre>
 * BaseVO          ← id / createTime / updateTime
 *   └── DataVO    ← + remark / enabled / sortOrder
 * </pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseVO extends BasePageVO {

    @Serial
    private static final long serialVersionUID = 1L;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
