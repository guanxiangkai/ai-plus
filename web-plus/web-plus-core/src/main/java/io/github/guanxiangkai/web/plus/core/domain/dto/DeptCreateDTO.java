package io.github.guanxiangkai.web.plus.core.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 部门创建 DTO（对应 {@link io.github.guanxiangkai.web.plus.core.entity.DeptEntity}）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "部门创建DTO")
public abstract class DeptCreateDTO extends TenantCreateDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "部门ID")
    private String deptId;

    @Schema(description = "部门名称")
    private String deptName;
}

