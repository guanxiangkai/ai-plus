package io.github.guanxiangkai.web.plus.core.entity;

/**
 * 部门感知接口
 * <p>
 * 实体实现此接口表示携带部门归属信息，支持部门级数据权限。
 * {@link DeptEntity} 默认实现了此接口。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface DeptAware {

    String getDeptId();

    void setDeptId(String deptId);

    String getDeptName();

    void setDeptName(String deptName);
}

