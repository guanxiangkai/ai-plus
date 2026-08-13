package io.github.guanxiangkai.jpa.plus.starter.tenant;

import io.github.guanxiangkai.jpa.plus.interceptor.tenant.interceptor.TenantInterceptor;
import io.github.guanxiangkai.jpa.plus.interceptor.tenant.spi.TenantIdProvider;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * Hibernate 层多租户隔离上下文。
 */
public final class HibernateTenantContext {

    public static final String SETTING_KEY = "io.github.guanxiangkai.jpa.plus.tenant.context";
    static final String FILTER_NAME = "_jpaPlusTenant";
    static final String PARAMETER_NAME = "tenantId";
    public static final String DEFAULT_TENANT_PROPERTY = "tenantId";
    public static final String DEFAULT_TENANT_COLUMN = TenantInterceptor.DEFAULT_TENANT_COLUMN;

    private final TenantIdProvider tenantIdProvider;
    private final String tenantProperty;
    private final String tenantColumn;
    private final Set<String> placeholderValues;

    public HibernateTenantContext(TenantIdProvider tenantIdProvider,
                                  String tenantProperty,
                                  String tenantColumn,
                                  Set<String> placeholderValues) {
        this.tenantIdProvider = tenantIdProvider;
        this.tenantProperty = StringUtils.hasText(tenantProperty) ? tenantProperty : DEFAULT_TENANT_PROPERTY;
        this.tenantColumn = StringUtils.hasText(tenantColumn) ? tenantColumn : DEFAULT_TENANT_COLUMN;
        this.placeholderValues = placeholderValues == null ? Set.of() : Set.copyOf(placeholderValues);
    }

    String tenantProperty() {
        return tenantProperty;
    }

    String tenantColumn() {
        return tenantColumn;
    }

    boolean shouldFill(Object currentValue) {
        if (currentValue == null) {
            return true;
        }
        if (!(currentValue instanceof String text)) {
            return false;
        }
        return !StringUtils.hasText(text) || placeholderValues.contains(text);
    }

    String requireTenantId() {
        String tenantId = tenantIdProvider.getCurrentTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalStateException("[jpa-plus] 当前租户 ID 为空，已拒绝执行租户实体的查询或保存。");
        }
        return tenantId;
    }
}
