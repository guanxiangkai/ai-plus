package io.github.guanxiangkai.jpa.plus.sharding.interceptor;

import io.github.guanxiangkai.jpa.plus.core.interceptor.Chain;
import io.github.guanxiangkai.jpa.plus.core.interceptor.DataInterceptor;
import io.github.guanxiangkai.jpa.plus.core.interceptor.Phase;
import io.github.guanxiangkai.jpa.plus.core.model.DataInvocation;
import io.github.guanxiangkai.jpa.plus.core.model.DeleteInvocation;
import io.github.guanxiangkai.jpa.plus.core.model.OperationType;
import io.github.guanxiangkai.jpa.plus.core.model.SaveInvocation;
import io.github.guanxiangkai.jpa.plus.sharding.model.ShardingContext;
import io.github.guanxiangkai.jpa.plus.sharding.model.ShardingTarget;
import io.github.guanxiangkai.jpa.plus.sharding.router.ShardingRouter;
import io.github.guanxiangkai.jpa.plus.sharding.util.LogicTableResolver;
import lombok.extern.slf4j.Slf4j;

/**
 * 分库分表前置拦截器
 *
 * <p>在 SAVE / DELETE 操作执行前（{@link Phase#BEFORE}），通过 {@link ShardingRouter}
 * 计算出目标库表，并将结果写入 {@link ShardingContext}（ScopedValue 作用域），
 * 同时调用 {@code JpaPlusContext.withDS()} 将数据源路由到目标库。</p>
 *
 * <h3>执行链路</h3>
 * <pre>
 * DataInterceptor Chain (BEFORE)
 *   │
 *   ▼
 * ShardingInterceptor.intercept(invocation, chain)
 *   │
 *   ①  从 entityClass 解析逻辑表名（{@code @Table(name=...)} 或类名小写）
 *   │
 *   ②  判断是否注册了分片规则（未注册则直接透传，不影响非分片表）
 *   │
 *   ③  调用 ShardingRouter.route(logicTable, entity) → ShardingTarget(db, table)
 *   │
 *   ④  在 ShardingContext + JpaPlusContext.withDS(target.db) 双重作用域中
 *       调用 chain.proceed(invocation)，执行后续拦截器和核心逻辑
 *   │
 *   ⑤  ScopedValue 作用域结束，自动恢复路由状态（无需手动清理）
 * </pre>
 *
 * <h3>跨分片防护</h3>
 * <p>单次调用只路由到一个分片。批量保存时若需要不同分片，应在应用层按分片键分组后分批调用。</p>
 *
 * <h3>非分片表透传</h3>
 * <p>若当前实体的逻辑表未在 {@link io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRuleRegistry} 中注册，
 * 拦截器直接透传，不修改任何状态，对非分片业务零影响。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class ShardingInterceptor implements DataInterceptor {

    /**
     * 执行优先级：50，在权限拦截器（通常 100+）之前，确保路由先于其他拦截器设置
     */
    private static final int ORDER = 50;

    private final ShardingRouter router;

    public ShardingInterceptor(ShardingRouter router) {
        this.router = router;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public Phase phase() {
        return Phase.BEFORE;
    }

    @Override
    public boolean supports(OperationType type) {
        // 仅拦截写操作（SAVE / DELETE）；查询路由由 @DS 或 @ShardingQuery 扩展处理
        return type == OperationType.SAVE || type == OperationType.DELETE;
    }

    @Override
    public Object intercept(DataInvocation invocation, Chain chain) throws Throwable {
        Object entity = switch (invocation) {
            case SaveInvocation si -> si.entity();
            case DeleteInvocation di -> di.entity();
            default -> null;
        };
        Class<?> entityClass = invocation.entityClass();

        // ① 解析逻辑表名
        String logicTable = LogicTableResolver.resolve(entityClass);

        // ② 未注册分片规则 → 透传，不影响非分片表
        if (!router.isSharded(logicTable)) {
            if (log.isTraceEnabled()) {
                log.trace("[jpa-plus-sharding] No sharding rule for table '{}', pass through.", logicTable);
            }
            return chain.proceed(invocation);
        }

        // P0: entity is null for deleteById / deleteAllById operations.
        // There is no sharding key to extract, so we must fail fast.
        // Silently passing through to the default datasource would execute the delete on the
        // wrong shard, causing a silent data-inconsistency (0 rows affected on the wrong DB).
        if (entity == null) {
            throw new UnsupportedOperationException(
                    "[jpa-plus-sharding] deleteById() / deleteAllById() 不支持分片表 '" + logicTable + "'。" +
                            "请先用 findById() 加载实体，再调用 delete(entity)，以确保分片键可用于路由。");
        }

        // ③ 计算分片目标
        ShardingTarget target = router.route(logicTable, entity);
        log.debug("[jpa-plus-sharding] Routing '{}' → db='{}', table='{}'",
                logicTable, target.db(), target.table());

        // ④ 在 ShardingContext + JpaPlusContext.withDS 双重作用域中执行后续链路
        // 显式声明 ThrowableCallable 类型，避免与 Callable 重载产生歧义
        io.github.guanxiangkai.jpa.plus.datasource.context.JpaPlusContext.ThrowableCallable<Object> innerTask =
                () -> chain.proceed(invocation);
        ShardingContext.ThrowableCallable<Object> outerTask =
                () -> io.github.guanxiangkai.jpa.plus.datasource.context.JpaPlusContext.withDS(target.db(), innerTask);
        return ShardingContext.withTarget(target, outerTask);
    }
}

