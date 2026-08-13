package io.github.guanxiangkai.jpa.plus.interceptor.tenant.interceptor;

import io.github.guanxiangkai.jpa.plus.core.interceptor.Chain;
import io.github.guanxiangkai.jpa.plus.core.interceptor.DataInterceptor;
import io.github.guanxiangkai.jpa.plus.core.interceptor.Phase;
import io.github.guanxiangkai.jpa.plus.core.model.DataInvocation;
import io.github.guanxiangkai.jpa.plus.core.model.OperationType;
import io.github.guanxiangkai.jpa.plus.core.model.QueryInvocation;
import io.github.guanxiangkai.jpa.plus.interceptor.tenant.spi.TenantIdProvider;
import io.github.guanxiangkai.jpa.plus.query.ast.Condition;
import io.github.guanxiangkai.jpa.plus.query.ast.Conditions;
import io.github.guanxiangkai.jpa.plus.query.ast.Eq;
import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;
import io.github.guanxiangkai.jpa.plus.query.context.QueryRuntime;
import io.github.guanxiangkai.jpa.plus.query.metadata.ColumnMeta;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * 多租户拦截器
 *
 * <p>在所有数据操作前自动注入 {@code <tenantColumn> = :tenantId} 条件，实现数据隔离。
 * 租户 ID 通过 {@link TenantIdProvider} 函数式接口获取，业务方只需注册一个
 * {@code TenantIdProvider} Bean 即可。</p>
 *
 * <p>租户列名默认为 {@code tenant_id}，可通过构造器参数或配置项
 * {@code jpa-plus.tenant.column} 自定义。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Bean
 * public TenantIdProvider tenantIdProvider() {
 *     return () -> SecurityContextHolder.getContext().getOrgId();
 * }
 *
 * // 可选：自定义列名
 * @Bean
 * public TenantInterceptor tenantInterceptor(TenantIdProvider provider) {
 *     return new TenantInterceptor("org_id", provider);
 * }
 * }</pre>
 *
 * <p><b>设计模式：</b>策略模式（Strategy） —— 租户 ID 获取逻辑由 {@link TenantIdProvider} 策略提供</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class TenantInterceptor implements DataInterceptor {

    /**
     * 默认租户列名
     */
    public static final String DEFAULT_TENANT_COLUMN = "tenant_id";

    private final String tenantColumn;
    private final TenantIdProvider tenantIdProvider;

    /**
     * 完整构造器
     *
     * @param tenantColumn     租户字段的数据库列名，例如 {@code "tenant_id"}、{@code "org_id"}
     * @param tenantIdProvider 租户 ID 提供者
     */
    public TenantInterceptor(String tenantColumn, TenantIdProvider tenantIdProvider) {
        if (tenantColumn == null || tenantColumn.isBlank()) {
            throw new IllegalArgumentException("tenantColumn must not be blank");
        }
        this.tenantColumn = tenantColumn;
        this.tenantIdProvider = Objects.requireNonNull(tenantIdProvider, "tenantIdProvider must not be null");
    }

    /**
     * 使用默认列名 {@code tenant_id} 的构造器
     *
     * @param tenantIdProvider 租户 ID 提供者
     */
    public TenantInterceptor(TenantIdProvider tenantIdProvider) {
        this(DEFAULT_TENANT_COLUMN, tenantIdProvider);
    }

    @Override
    public int order() {
        return 150;
    }

    @Override
    public Phase phase() {
        return Phase.BEFORE;
    }

    @Override
    public boolean supports(OperationType type) {
        // TenantInterceptor only injects tenant isolation on QUERY operations.
        // SAVE and DELETE operations are intentionally not intercepted: silently injecting
        // a tenant condition on writes without the caller's knowledge could cause subtle bugs.
        // Cross-tenant write prevention should be enforced at the service/application layer.
        return type == OperationType.QUERY;
    }

    @Override
    public Object intercept(DataInvocation invocation, Chain chain) throws Throwable {
        if (invocation instanceof QueryInvocation qi && qi.queryContext() instanceof QueryContext ctx) {
            String tenantId = tenantIdProvider.getCurrentTenantId();
            // P0: null tenant MUST fail closed — silently omitting the condition would leak all tenants' data.
            if (tenantId == null) {
                throw new IllegalStateException(
                        "[jpa-plus] TenantInterceptor: cannot determine current tenant ID. " +
                                "Ensure TenantIdProvider returns a non-null value for authenticated requests. " +
                                "If this operation is intentionally tenant-agnostic, mark the Repository method " +
                                "with @DS or bypass the interceptor via TenantInterceptor.skipFilter().");
            }
            Condition tenantCondition = new Eq(
                    ColumnMeta.of(ctx.metadata().root(), tenantColumn, String.class),
                    tenantId
            );
            Condition combined = Conditions.and(ctx.runtime().where(), tenantCondition);
            QueryRuntime newRuntime = ctx.runtime().withWhere(combined);
            invocation = qi.withQueryContext(ctx.withRuntime(newRuntime));

            if (log.isDebugEnabled()) {
                log.debug("[jpa-plus] TenantInterceptor: injecting {}={}", tenantColumn, tenantId);
            }
        }
        return chain.proceed(invocation);
    }

    /**
     * 获取当前配置的租户列名
     */
    public String tenantColumn() {
        return tenantColumn;
    }
}
