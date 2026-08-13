package io.github.guanxiangkai.web.plus.security.context;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 当前登录用户上下文（不可变值对象）
 * <p>
 * 由 {@code HeaderAuthenticationFilter} 从网关透传的请求头中构建，
 * 存储在 {@link UserContextHolder} 的 ThreadLocal 中，
 * 通过 Micrometer Context Propagation 自动跨 Reactor 线程传播。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public record UserContext(
        String userId,
        String tenantId,
        boolean superAdmin,
        String deptId,
        Set<String> deptIds,
        Set<String> roles,
        Set<String> permissions,
        Map<String, Object> claims
) {

    public UserContext {
        deptIds = deptIds != null ? Collections.unmodifiableSet(deptIds) : Set.of();
        roles = roles != null ? Collections.unmodifiableSet(roles) : Set.of();
        permissions = permissions != null ? Collections.unmodifiableSet(permissions) : Set.of();
        claims = claims != null ? Collections.unmodifiableMap(claims) : Map.of();
    }
}
