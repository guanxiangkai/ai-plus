package io.github.guanxiangkai.web.plus.security.authorization;

import java.util.Set;

/**
 * 服务端解析出的当前用户授权范围。
 */
public record AuthorizationScope(
        Set<String> roles,
        Set<String> permissions,
        Set<String> deptIds
) {

    public static final AuthorizationScope EMPTY = new AuthorizationScope(Set.of(), Set.of(), Set.of());

    public AuthorizationScope {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        deptIds = deptIds == null ? Set.of() : Set.copyOf(deptIds);
    }
}
