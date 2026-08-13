package io.github.guanxiangkai.web.plus.core.domain.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 租户更新 DTO（对应 {@link io.github.guanxiangkai.web.plus.core.entity.TenantEntity}）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class TenantDTO extends DataDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tenantId;
}

