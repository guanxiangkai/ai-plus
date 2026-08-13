package io.github.guanxiangkai.web.plus.core.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 基础创建 DTO（无 ID 字段，用于新增操作）
 * <p>
 * 创建操作不需要提供主键，由服务端自动生成 UUID。
 * </p>
 *
 * <h3>继承关系</h3>
 * <pre>
 * BaseCreateDTO          ← 无 id
 *   └── DataCreateDTO    ← + remark / sortOrder
 * </pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Data
@Schema(description = "基础创建DTO")
public abstract class BaseCreateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
