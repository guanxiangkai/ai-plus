package io.github.guanxiangkai.web.plus.core.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * 带常用数据字段的部门实体基类。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Getter
@Setter
@jakarta.persistence.MappedSuperclass
public abstract class DataDeptEntity extends DeptEntity implements Enableable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 备注
     */
    @jakarta.persistence.Column(name = "remark", length = 500, comment = "备注")
    private String remark;

    /**
     * 是否启用（默认启用）
     */
    @jakarta.persistence.Column(name = "enabled", comment = "是否启用")
    private Boolean enabled = true;

    /**
     * 状态
     */
    @jakarta.persistence.Column(name = "status", length = 20, comment = "状态")
    private String status;
}
