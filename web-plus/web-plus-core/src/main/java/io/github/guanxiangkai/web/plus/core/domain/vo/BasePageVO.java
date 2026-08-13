package io.github.guanxiangkai.web.plus.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 基础分页列表视图对象（对应 {@link io.github.guanxiangkai.web.plus.core.entity.BaseEntity}）
 * <p>
 * 分页列表场景只需展示摘要信息，仅包含主键 ID，子类按需扩展列表展示字段。
 * </p>
 *
 * <h3>继承关系</h3>
 * <pre>
 * BasePageVO          ← id
 *   └── DataPageVO    ← + enabled / sortOrder
 * </pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Data
public abstract class BasePageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
}

