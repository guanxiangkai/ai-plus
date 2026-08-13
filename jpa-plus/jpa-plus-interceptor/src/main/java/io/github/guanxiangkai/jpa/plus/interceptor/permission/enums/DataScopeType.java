package io.github.guanxiangkai.jpa.plus.interceptor.permission.enums;

/**
 * 内置数据权限范围枚举
 *
 * <p>定义行级数据权限的五种常见控制粒度。
 * 实现 {@link DataScopeEnum} 接口，用户可自定义枚举实现该接口以扩展权限粒度。</p>
 *
 * @author guanxiangkai
 * @see DataScopeEnum
 * @since 1.0.0
 */
public enum DataScopeType implements DataScopeEnum {

    /**
     * 全部数据 —— 不追加任何权限条件
     */
    ALL {
        @Override
        public boolean skipFilter() {
            return true;
        }
    },

    /**
     * 本部门数据 —— 追加 {@code dept_id = :currentDeptId}
     */
    DEPT,

    /**
     * 本部门及子部门数据 —— 追加 {@code dept_id IN (:deptIds)}
     */
    DEPT_AND_CHILD,

    /**
     * 仅自己的数据 —— 追加 {@code create_by = :currentUserId}
     */
    SELF,

    /**
     * 自定义权限 —— 由 {@code DataScopeHandler#customCondition} 完全控制
     */
    CUSTOM;

    @Override
    public String scopeName() {
        return name();
    }
}
