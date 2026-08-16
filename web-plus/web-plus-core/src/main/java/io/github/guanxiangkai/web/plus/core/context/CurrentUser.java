package io.github.guanxiangkai.web.plus.core.context;

import jakarta.persistence.Column;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 当前登录用户上下文（不可变值对象）
 * <p>
 * 由认证过滤器解析 Token 后构建，存储在 {@link CurrentUserHolder} 的 ThreadLocal 中，
 * 通过 {@code SecurityUtils} 在任意业务层获取。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public record CurrentUser(
        String userId,
        String nickname,
        String tenantId,
        String deptId,
        Set<String> deptIds,
        Set<String> roles,
        Set<String> permissions,
        @Column(name = "super_admin", comment = "是否超级管理员")
        Boolean superAdmin,
        String deviceType,
        long loginTime,
        Map<String, Object> extraClaims
) {

    public CurrentUser {
        deptIds = deptIds != null ? Collections.unmodifiableSet(deptIds) : Set.of();
        roles = roles != null ? Collections.unmodifiableSet(roles) : Set.of();
        permissions = permissions != null ? Collections.unmodifiableSet(permissions) : Set.of();
        superAdmin = Boolean.TRUE.equals(superAdmin);
        extraClaims = extraClaims != null ? Collections.unmodifiableMap(extraClaims) : Map.of();
    }

    /**
     * 快速构建（仅 userId）
     */
    public static CurrentUser ofUserId(String userId) {
        return new CurrentUser(userId, null, null, null,
                Set.of(), Set.of(), Set.of(), false, null, System.currentTimeMillis(), Map.of());
    }

    /**
     * 返回不包含用户身份、租户、部门、授权范围和扩展声明的诊断摘要。
     *
     * @return 已脱敏的当前用户摘要
     */
    @Override
    public String toString() {
        return "CurrentUser[identity=<redacted>, superAdmin=" + superAdmin
                + ", loginTime=" + loginTime + ", authorization=<redacted>]";
    }

}
