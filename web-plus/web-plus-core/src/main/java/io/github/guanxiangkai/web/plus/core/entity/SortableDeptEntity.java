package io.github.guanxiangkai.web.plus.core.entity;

import jakarta.persistence.Embedded;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * 支持排序的部门实体基类。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Getter
@Setter
@jakarta.persistence.MappedSuperclass
public abstract class SortableDeptEntity extends DataDeptEntity implements Sortable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 排序信息
     */
    @Embedded
    private SortInfo sortInfo = new SortInfo();
}
