package io.github.guanxiangkai.web.plus.core.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 部门分页 VO（对应 {@link io.github.guanxiangkai.web.plus.core.entity.DeptEntity}）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class DeptPageVO extends TenantPageVO {

    @Serial
    private static final long serialVersionUID = 1L;

    private String deptId;

    private String deptName;
}

