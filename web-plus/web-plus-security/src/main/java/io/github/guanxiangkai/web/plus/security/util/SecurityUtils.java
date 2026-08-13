package io.github.guanxiangkai.web.plus.security.util;

import io.github.guanxiangkai.web.plus.security.context.UserContext;
import io.github.guanxiangkai.web.plus.security.context.UserContextHolder;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * 安全工具类
 * <p>
 * 从 {@link UserContextHolder} 的 ThreadLocal 中同步获取当前登录用户信息。
 * ThreadLocal 由 {@code HeaderAuthenticationFilter} 填充，
 * 并通过 Micrometer Context Propagation 跨 Reactor 线程自动传播。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public final class SecurityUtils {

    private SecurityUtils() {
        throw new UnsupportedOperationException("实用类");
    }

    // ==================== 用户身份 ====================

    /**
     * 获取当前用户 ID
     *
     * @return 用户 ID，未认证返回 null
     */
    public static String getUserId() {
        UserContext ctx = UserContextHolder.get();
        return ctx != null ? ctx.userId() : null;
    }

    /**
     * 获取当前租户 ID
     *
     * @return 租户 ID，未设置返回 null
     */
    public static String getTenantId() {
        UserContext ctx = UserContextHolder.get();
        return ctx != null ? ctx.tenantId() : null;
    }

    /**
     * 判断是否为超级管理员
     *
     * @return true=超级管理员，未认证返回 false
     */
    public static boolean isSuperAdmin() {
        UserContext ctx = UserContextHolder.get();
        return ctx != null && ctx.superAdmin();
    }

    /**
     * 判断是否已认证
     *
     * @return true=已登录
     */
    public static boolean isAuthenticated() {
        return UserContextHolder.get() != null;
    }

    // ==================== 部门 ====================

    /**
     * 获取当前用户的部门 ID
     *
     * @return 部门 ID，未设置返回 null
     */
    public static String getDeptId() {
        UserContext ctx = UserContextHolder.get();
        return ctx != null ? ctx.deptId() : null;
    }

    /**
     * 获取当前用户可访问的部门 ID 集合
     *
     * @return 部门 ID 集合，未认证返回空集
     */
    public static Set<String> getDeptIds() {
        UserContext ctx = UserContextHolder.get();
        return ctx != null ? ctx.deptIds() : Set.of();
    }

    // ==================== 权限 ====================

    /**
     * 获取当前用户权限集合
     *
     * @return 权限代码集合，未认证返回空集
     */
    public static Set<String> getPermissions() {
        UserContext ctx = UserContextHolder.get();
        return ctx != null ? ctx.permissions() : Set.of();
    }

    public static Set<String> getRoles() {
        UserContext ctx = UserContextHolder.get();
        return ctx != null ? ctx.roles() : Set.of();
    }

    /**
     * 判断当前用户是否拥有任一权限
     *
     * @param permissions 权限代码数组
     * @return true=拥有其中任一
     */
    public static boolean hasAnyPermission(String... permissions) {
        if (permissions == null || permissions.length == 0) return false;
        Set<String> userPerms = getPermissions();
        for (String p : permissions) {
            if (userPerms.contains(p)) return true;
        }
        return false;
    }

    /**
     * 判断当前用户是否拥有所有权限
     *
     * @param permissions 权限代码数组
     * @return true=全部拥有
     */
    public static boolean hasAllPermissions(String... permissions) {
        if (permissions == null || permissions.length == 0) return true;
        Set<String> userPerms = getPermissions();
        for (String p : permissions) {
            if (!userPerms.contains(p)) return false;
        }
        return true;
    }
}
