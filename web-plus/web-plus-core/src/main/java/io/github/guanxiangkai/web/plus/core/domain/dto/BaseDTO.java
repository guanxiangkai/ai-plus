package io.github.guanxiangkai.web.plus.core.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 基础更新 DTO（有 ID 字段，用于更新操作）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Data
@Schema(description = "基础更新DTO")
public abstract class BaseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID（更新时必填）")
    private String id;
}
