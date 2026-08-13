package io.github.guanxiangkai.web.plus.core.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 租户创建 DTO（对应 {@link io.github.guanxiangkai.web.plus.core.entity.TenantEntity}）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "租户创建DTO")
public abstract class TenantCreateDTO extends DataCreateDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "租户ID")
    private String tenantId;
}

