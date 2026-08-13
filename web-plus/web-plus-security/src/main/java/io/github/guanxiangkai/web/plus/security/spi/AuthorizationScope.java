package io.github.guanxiangkai.web.plus.security.spi;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 当前用户授权范围。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public record AuthorizationScope(
        Set<String> roles,
        Set<String> permissions,
        Set<String> deptIds
) {

    public static final AuthorizationScope EMPTY = new AuthorizationScope(Set.of(), Set.of(), Set.of());

    public AuthorizationScope {
        roles = roles != null ? Collections.unmodifiableSet(new LinkedHashSet<>(roles)) : Set.of();
        permissions = permissions != null ? Collections.unmodifiableSet(new LinkedHashSet<>(permissions)) : Set.of();
        deptIds = deptIds != null ? Collections.unmodifiableSet(new LinkedHashSet<>(deptIds)) : Set.of();
    }
}
