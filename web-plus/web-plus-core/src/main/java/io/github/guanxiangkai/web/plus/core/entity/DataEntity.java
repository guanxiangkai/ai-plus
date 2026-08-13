package io.github.guanxiangkai.web.plus.core.entity;

import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * 数据实体基类
 * <p>
 * 在 {@link BaseEntity} 基础上增加常用业务字段：备注、启用状态、状态。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Getter
@Setter
@jakarta.persistence.MappedSuperclass
public abstract class DataEntity extends BaseEntity implements Enableable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 备注
     */
    @Column(name = "remark", length = 500, comment = "备注")
    private String remark;

    /**
     * 是否启用（默认启用）
     */
    @Column(name = "enabled", comment = "是否启用")
    private Boolean enabled = true;

    /**
     * 状态
     */
    @Column(name = "status", length = 20, comment = "状态")
    private String status;
}
