package io.github.guanxiangkai.web.plus.security.authorization;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Spring Security 方法权限工厂，统一支持超级管理员短路放行。
 */
public final class SuperAdminAuthorizationManagerFactory<T extends @Nullable Object>
        implements AuthorizationManagerFactory<T> {

    private static final AuthorizationDecision GRANTED = new AuthorizationDecision(true);
    private static final String SUPER_ADMIN_AUTHORITY = "SUPER_ADMIN";

    @Override
    public AuthorizationManager<T> hasRole(String role) {
        return withSuperAdminBypass(AuthorityAuthorizationManager.hasRole(role));
    }

    @Override
    public AuthorizationManager<T> hasAnyRole(String... roles) {
        return withSuperAdminBypass(AuthorityAuthorizationManager.hasAnyRole(roles));
    }

    @Override
    public AuthorizationManager<T> hasAllRoles(String... roles) {
        return withSuperAdminBypass(AllAuthoritiesAuthorizationManager.hasAllRoles(roles));
    }

    @Override
    public AuthorizationManager<T> hasAuthority(String authority) {
        return withSuperAdminBypass(AuthorityAuthorizationManager.hasAuthority(authority));
    }

    @Override
    public AuthorizationManager<T> hasAnyAuthority(String... authorities) {
        return withSuperAdminBypass(AuthorityAuthorizationManager.hasAnyAuthority(authorities));
    }

    @Override
    public AuthorizationManager<T> hasAllAuthorities(String... authorities) {
        return withSuperAdminBypass(AllAuthoritiesAuthorizationManager.hasAllAuthorities(authorities));
    }

    private AuthorizationManager<T> withSuperAdminBypass(AuthorizationManager<T> delegate) {
        return (authentication, object) -> {
            Authentication current = authentication.get();
            if (isSuperAdmin(current)) {
                return GRANTED;
            }
            return delegate.authorize(authentication, object);
        };
    }

    private boolean isSuperAdmin(@Nullable Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (SUPER_ADMIN_AUTHORITY.equals(authority.getAuthority())) {
                return true;
            }
        }
        Object details = authentication.getDetails();
        if (details instanceof Map<?, ?> claims) {
            return toBoolean(claims.get("superAdmin"));
        }
        return readSuperAdmin(details);
    }

    private boolean readSuperAdmin(@Nullable Object details) {
        if (details == null) {
            return false;
        }
        try {
            Method method = details.getClass().getMethod("superAdmin");
            return toBoolean(method.invoke(details));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean toBoolean(@Nullable Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }
}
