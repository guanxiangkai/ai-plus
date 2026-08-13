package io.github.guanxiangkai.jpa.plus.starter.tenant;

import java.util.function.Supplier;

/**
 * Hibernate Filter 参数解析器。
 */
final class JpaPlusTenantParameterResolver implements Supplier<String> {

    private final HibernateTenantContext tenantContext;

    JpaPlusTenantParameterResolver(HibernateTenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    public String get() {
        return tenantContext.requireTenantId();
    }
}
