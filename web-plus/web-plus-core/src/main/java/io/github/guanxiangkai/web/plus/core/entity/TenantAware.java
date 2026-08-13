package io.github.guanxiangkai.web.plus.core.entity;

/**
 * 租户感知接口
 * <p>
 * 实体实现此接口表示支持多租户隔离。
 * {@link TenantEntity} 默认实现了此接口。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface TenantAware {

    String getTenantId();

    void setTenantId(String tenantId);
}

