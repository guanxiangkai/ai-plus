package io.github.guanxiangkai.web.plus.core.entity;

import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * 部门实体基类
 * <p>
 * 在 {@link TenantEntity} 基础上增加 {@code deptId} / {@code deptName}，
 * 支持部门级数据权限控制。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Getter
@Setter
@jakarta.persistence.MappedSuperclass
public abstract class DeptEntity extends TenantEntity implements DeptAware {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 部门 ID
     */
    @Column(name = "dept_id", length = 64, comment = "部门ID")
    private String deptId;

    /**
     * 部门名称（冗余存储，避免关联查询）
     */
    @Column(name = "dept_name", length = 100, comment = "部门名称")
    private String deptName;
}
