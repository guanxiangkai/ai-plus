package io.github.guanxiangkai.web.plus.core.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 数据更新 DTO（对应 DataEntity）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "数据更新DTO")
public abstract class DataDTO extends BaseDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;

    @Schema(description = "排序号")
    private Integer sortOrder;
}
