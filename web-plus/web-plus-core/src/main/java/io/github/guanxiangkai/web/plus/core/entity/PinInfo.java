package io.github.guanxiangkai.web.plus.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 置顶信息组件。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Getter
@Setter
@Embeddable
public class PinInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否置顶
     */
    @Column(name = "pinned", nullable = false, comment = "是否置顶")
    private Boolean pinned = false;
}
