package io.github.guanxiangkai.web.plus.core.entity;

import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * 租户实体基类
 * <p>
 * 在 {@link BaseEntity} 基础上增加 {@code tenantId} 字段，配合 jpa-plus
 * {@code TenantInterceptor} 与 Hibernate Filter 实现透明多租户数据隔离。
 * </p>
 *
 * <h3>自动桥接（引入 jpa-plus-interceptor 时自动生效）</h3>
 * <p>
 * {@code web-plus-web} 会自动注册 {@code TenantIdProvider} Bean，
 * 从 {@code CurrentUserProvider} 读取当前登录用户的 {@code tenantId}。
 * 业务侧无需手动注册，jpa-plus 会在 QueryWrapper 查询和 Hibernate 普通查询中
 * 追加租户隔离条件，并在保存租户实体前补齐租户 ID。
 * </p>
 *
 * <h3>自定义租户来源</h3>
 * <pre>{@code
 * @Bean
 * public TenantIdProvider tenantIdProvider() {
 *     // 例如从 MDC、请求头或其他上下文读取
 *     return () -> MDC.get("tenantId");
 * }
 * }</pre>
 *
 * <h3>自定义租户字段</h3>
 * <pre>{@code
 * jpa-plus:
 *   tenant:
 *     property: tenantId
 *     column: tenant_id
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Getter
@Setter
@jakarta.persistence.MappedSuperclass
public abstract class TenantEntity extends BaseEntity implements TenantAware {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 租户 ID，由 jpa-plus 在保存前按当前 {@code TenantIdProvider} 自动补齐。
     */
    @Column(name = "tenant_id", nullable = false, length = 64, comment = "租户ID")
    private String tenantId;
}
