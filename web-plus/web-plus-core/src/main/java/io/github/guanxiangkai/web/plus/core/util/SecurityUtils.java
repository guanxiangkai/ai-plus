package io.github.guanxiangkai.web.plus.core.util;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;

import java.util.Set;

/**
 * 安全工具类 —— 从 ThreadLocal 读取当前用户信息
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前用户 ID，未认证返回 {@code null}
     */
    public static String getUserId() {
        CurrentUser user = CurrentUserHolder.get();
        return user != null ? user.userId() : null;
    }

    /**
     * 获取当前用户昵称，未认证返回 {@code null}
     */
    public static String getUsername() {
        CurrentUser user = CurrentUserHolder.get();
        return user != null ? user.nickname() : null;
    }

    /**
     * 获取当前租户 ID，未认证返回 {@code null}
     */
    public static String getTenantId() {
        CurrentUser user = CurrentUserHolder.get();
        return user != null ? user.tenantId() : null;
    }

    /**
     * 获取当前部门 ID，未认证返回 {@code null}
     */
    public static String getDeptId() {
        CurrentUser user = CurrentUserHolder.get();
        return user != null ? user.deptId() : null;
    }

    /**
     * 获取当前用户角色集合
     */
    public static Set<String> getRoles() {
        CurrentUser user = CurrentUserHolder.get();
        return user != null ? user.roles() : Set.of();
    }

    /**
     * 获取当前用户权限集合
     */
    public static Set<String> getPermissions() {
        CurrentUser user = CurrentUserHolder.get();
        return user != null ? user.permissions() : Set.of();
    }

    /**
     * 判断是否超级管理员
     */
    public static boolean isSuperAdmin() {
        CurrentUser user = CurrentUserHolder.get();
        return user != null && Boolean.TRUE.equals(user.superAdmin());
    }

    /**
     * 判断是否已认证
     */
    public static boolean isAuthenticated() {
        return CurrentUserHolder.exists();
    }

    /**
     * 判断是否拥有某权限
     */
    public static boolean hasPermission(String permission) {
        return isSuperAdmin() || getPermissions().contains(permission);
    }

    /**
     * 判断是否拥有任一权限
     */
    public static boolean hasAnyPermission(String... permissions) {
        if (isSuperAdmin()) return true;
        Set<String> userPerms = getPermissions();
        for (String p : permissions) {
            if (userPerms.contains(p)) return true;
        }
        return false;
    }

    /**
     * 判断是否拥有某角色
     */
    public static boolean hasRole(String role) {
        return isSuperAdmin() || getRoles().contains(role);
    }
}
