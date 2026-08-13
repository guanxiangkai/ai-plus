package io.github.guanxiangkai.web.plus.security.spi;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;

/**
 * 权限判定 SPI
 * <p>
 * 业务侧可实现此接口，提供自定义的权限判定逻辑（如从数据库查询动态权限、支持通配符等）。
 * 默认实现基于 {@link CurrentUser#permissions()} 和 {@link CurrentUser#roles()} 集合做精确匹配。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface PermissionResolver {

    /**
     * 判断用户是否拥有指定权限
     *
     * @param user       当前用户（已认证）
     * @param permission 权限标识，如 {@code sys:user:list}
     * @return {@code true} 表示有权限
     */
    boolean hasPermission(CurrentUser user, String permission);

    /**
     * 判断用户是否拥有指定角色
     *
     * @param user 当前用户（已认证）
     * @param role 角色标识，如 {@code ADMIN}
     * @return {@code true} 表示拥有该角色
     */
    boolean hasRole(CurrentUser user, String role);

    /**
     * 判断用户是否拥有所有指定角色（AND）
     *
     * @param user  当前用户（已认证）
     * @param roles 角色列表
     * @return {@code true} 表示全部满足
     */
    default boolean hasAllRoles(CurrentUser user, String[] roles) {
        for (String role : roles) {
            if (!hasRole(user, role)) return false;
        }
        return true;
    }

    /**
     * 判断用户是否拥有任意一个指定角色（OR）
     *
     * @param user  当前用户（已认证）
     * @param roles 角色列表
     * @return {@code true} 表示至少拥有一个
     */
    default boolean hasAnyRole(CurrentUser user, String[] roles) {
        for (String role : roles) {
            if (hasRole(user, role)) return true;
        }
        return false;
    }
}

