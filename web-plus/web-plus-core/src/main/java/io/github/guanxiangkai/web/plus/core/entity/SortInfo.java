package io.github.guanxiangkai.web.plus.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 排序信息组件。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Getter
@Setter
@Embeddable
public class SortInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 显示排序号（数值越小越靠前，默认 0）
     */
    @Column(name = "sort_order", comment = "排序号")
    private Integer sortOrder = 0;
}
