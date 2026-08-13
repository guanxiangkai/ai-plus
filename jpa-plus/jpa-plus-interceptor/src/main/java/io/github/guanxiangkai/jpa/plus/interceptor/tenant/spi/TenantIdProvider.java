package io.github.guanxiangkai.jpa.plus.interceptor.tenant.spi;

/**
 * 租户 ID 提供者（函数式接口）
 *
 * <p>业务方实现此接口，从安全上下文（如 Spring Security SecurityContext、JWT Token 等）
 * 中获取当前请求的租户 ID。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * @Bean
 * public TenantIdProvider tenantIdProvider() {
 *     return () -> SecurityContextHolder.getContext().getOrgId();
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface TenantIdProvider {

    /**
     * 获取当前租户 ID
     *
     * @return 租户 ID；返回 {@code null} 表示不启用租户隔离（跳过条件注入）
     */
    String getCurrentTenantId();
}

