package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.interceptor.orderby.interceptor.AutoOrderByInterceptor;
import io.github.guanxiangkai.jpa.plus.interceptor.tenant.interceptor.TenantInterceptor;
import io.github.guanxiangkai.jpa.plus.interceptor.tenant.spi.TenantIdProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * JPA Plus 拦截器自动装配
 *
 * <p>负责注册框架内置的数据拦截器：</p>
 * <ul>
 *   <li>{@link AutoOrderByInterceptor} —— 自动排序（总是注册）</li>
 *   <li>{@link TenantInterceptor} —— 多租户（需业务方注册 {@link TenantIdProvider} Bean 后自动生效）</li>
 * </ul>
 *
 * <p>数据权限拦截器（{@code PermissionInterceptor}）需要 {@code DataScopeHandler} 实现，
 * 由业务方通过注册 {@code DataScopeHandler} Bean 触发自动配置，此处不默认注册。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@AutoConfiguration
public class JpaPlusInterceptorAutoConfiguration {

    // ─────────── 自动排序拦截器 ───────────

    @Bean
    @ConditionalOnMissingBean
    AutoOrderByInterceptor autoOrderByInterceptor() {
        return new AutoOrderByInterceptor();
    }

    // ─────────── 多租户拦截器（租户列名可配置） ───────────

    /**
     * 多租户拦截器自动注册
     *
     * <p>当业务方注册了 {@link TenantIdProvider} Bean 后自动生效。</p>
     *
     * <p>业务方实现示例：</p>
     * <pre>{@code
     * @Bean
     * public TenantIdProvider tenantIdProvider() {
     *     return () -> SecurityContextHolder.getContext().getTenantId();
     * }
     *
     * // 可选：自定义列名（默认 tenant_id，可通过 jpa-plus.tenant.column 配置）
     * }</pre>
     */
    @Bean
    @ConditionalOnMissingBean(TenantInterceptor.class)
    @ConditionalOnBean(TenantIdProvider.class)
    TenantInterceptor tenantInterceptor(
            TenantIdProvider tenantIdProvider,
            @Value("${jpa-plus.tenant.column:tenant_id}") String tenantColumn) {
        return new TenantInterceptor(tenantColumn, tenantIdProvider);
    }
}
