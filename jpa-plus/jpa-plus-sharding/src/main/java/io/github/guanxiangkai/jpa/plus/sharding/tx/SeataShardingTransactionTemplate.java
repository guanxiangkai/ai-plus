package io.github.guanxiangkai.jpa.plus.sharding.tx;

import io.github.guanxiangkai.jpa.plus.datasource.context.JpaPlusContext;
import io.github.guanxiangkai.jpa.plus.sharding.model.ShardingTarget;
import io.github.guanxiangkai.jpa.plus.sharding.router.ShardingRouter;
import io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRule;
import io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRuleRegistry;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Seata 跨分片事务辅助模板（SPI 接入参考实现）
 *
 * <p>本类仅作为接入模板示例，展示如何将 JPA Plus 的多分片写操作
 * 包裹在 Seata 全局事务中。<b>使用前必须在项目中引入 Seata 客户端依赖。</b></p>
 *
 * <h3>依赖要求</h3>
 * <pre>{@code
 * // build.gradle.kts（Seata 由业务方按需引入，JPA Plus 不内置）
 * implementation("io.seata:seata-spring-boot-starter:1.8.0")
 * }</pre>
 *
 * <h3>接入步骤</h3>
 * <ol>
 *   <li>配置 {@code jpa-plus.sharding.cross-shard-policy=SEATA}</li>
 *   <li>实现 {@link io.github.guanxiangkai.jpa.plus.datasource.spi.DataSourcePostProcessor}，
 *       将参与事务的数据源包装为 Seata 代理数据源</li>
 *   <li>注入此模板并替换原直接写操作</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Autowired
 * private SeataShardingTransactionTemplate seataTemplate;
 *
 * // 跨分片批量写入（在同一 Seata 全局事务中）
 * seataTemplate.executeInGlobalTx("batch-save-orders", () -> {
 *     for (Order order : orders) {
 *         orderRepository.save(order);  // ShardingInterceptor 自动路由
 *     }
 *     return null;
 * });
 * }</pre>
 *
 * <h3>事务边界说明</h3>
 * <p>单分片操作由 JPA 本地事务保证 ACID；跨分片操作需 Seata AT 模式保证最终一致性。
 * 在未引入 Seata 时，跨分片写入应由业务层拆分为多次单分片调用。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class SeataShardingTransactionTemplate {

    private static final SeataGlobalTransactionApi SEATA_API = SeataGlobalTransactionApi.resolve();

    private final ShardingRouter router;
    private final ShardingRuleRegistry registry;

    public SeataShardingTransactionTemplate(ShardingRouter router, ShardingRuleRegistry registry) {
        this.router = router;
        this.registry = registry;
    }

    /**
     * 在 Seata 全局事务上下文中执行跨分片操作
     *
     * <p>本方法通过反射调用 Seata {@code GlobalTransaction} API，避免直接依赖 Seata 类。
     * 实际使用中建议直接使用 {@code @GlobalTransactional} 注解。</p>
     *
     * @param txName 事务名称（用于日志追踪）
     * @param task   业务操作（可包含多个分片写入）
     * @param <T>    返回值类型
     * @return 操作结果
     * @throws Exception 业务异常或事务异常
     */
    public <T> T executeInGlobalTx(String txName, Callable<T> task) throws Exception {
        if (!SEATA_API.available()) {
            log.warn("[jpa-plus] SeataShardingTransactionTemplate.executeInGlobalTx('{}') called but " +
                    "Seata is NOT on the classpath. No distributed transaction guarantee is provided. " +
                    "Add io.seata:seata-spring-boot-starter to your dependencies and configure Seata " +
                    "to enable cross-shard distributed transactions.", txName);
            throw new UnsupportedOperationException(
                    "Seata is not available. Cannot execute '" + txName + "' in a global transaction. " +
                            "Add io.seata:seata-spring-boot-starter to enable this feature.",
                    SEATA_API.failure());
        }

        try {
            Object tx = SEATA_API.currentOrCreate();
            SEATA_API.begin(tx, 60_000, txName);
            log.debug("[jpa-plus] Seata global transaction '{}' started", txName);

            try {
                T result = task.call();
                SEATA_API.commit(tx);
                log.debug("[jpa-plus] Seata global transaction '{}' committed", txName);
                return result;
            } catch (Exception taskEx) {
                try {
                    SEATA_API.rollback(tx);
                    log.warn("[jpa-plus] Seata global transaction '{}' rolled back due to: {}",
                            txName, taskEx.getMessage());
                } catch (Exception rollbackEx) {
                    log.error("[jpa-plus] Seata rollback failed for transaction '{}'", txName, rollbackEx);
                    taskEx.addSuppressed(rollbackEx);
                }
                throw taskEx;
            }
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException(
                    "Failed to invoke Seata GlobalTransaction API via reflection for transaction '" + txName +
                            "'. Ensure io.seata:seata-spring-boot-starter is on the classpath.", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof Exception ex) throw ex;
            throw new RuntimeException("Unexpected error in Seata transaction '" + txName + "'", cause);
        }
    }

    /**
     * 按逻辑表名获取所有分片目标（用于散射写入场景）
     *
     * @param logicTable 逻辑表名
     * @return 所有分片目标列表
     */
    public List<ShardingTarget> allTargets(String logicTable) {
        ShardingRule rule = registry.find(logicTable);
        if (rule == null) return List.of();
        List<ShardingTarget> targets = new ArrayList<>();
        for (int db = 0; db < rule.dbCount(); db++) {
            for (int table = 0; table < rule.tableCount(); table++) {
                targets.add(new ShardingTarget(rule.resolveDb(db), rule.resolveTable(table)));
            }
        }
        return targets;
    }

    /**
     * 在指定分片目标的数据源上下文中执行操作
     *
     * @param target 分片目标（含目标库名）
     * @param task   在该数据源上执行的操作
     * @param <T>    返回值类型
     */
    public <T> T executeOnShard(ShardingTarget target, JpaPlusContext.ThrowableCallable<T> task) throws Throwable {
        return JpaPlusContext.withDS(target.db(), task);
    }

    /**
     * Cached reflection facade for optional Seata integration.
     */
    private record SeataGlobalTransactionApi(boolean available,
                                             Throwable failure,
                                             Method getCurrentOrCreate,
                                             Method begin,
                                             Method commit,
                                             Method rollback) {

        static SeataGlobalTransactionApi resolve() {
            try {
                Class.forName("io.seata.core.context.RootContext");
                Class<?> globalTxCtxClass = Class.forName("io.seata.tm.api.GlobalTransactionContext");
                Class<?> globalTxClass = Class.forName("io.seata.tm.api.GlobalTransaction");
                return new SeataGlobalTransactionApi(
                        true,
                        null,
                        globalTxCtxClass.getMethod("getCurrentOrCreate"),
                        globalTxClass.getMethod("begin", int.class, String.class),
                        globalTxClass.getMethod("commit"),
                        globalTxClass.getMethod("rollback")
                );
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                return new SeataGlobalTransactionApi(false, e, null, null, null, null);
            }
        }

        Object currentOrCreate() throws InvocationTargetException, IllegalAccessException {
            return getCurrentOrCreate.invoke(null);
        }

        void begin(Object tx, int timeoutMillis, String txName) throws InvocationTargetException, IllegalAccessException {
            begin.invoke(tx, timeoutMillis, txName);
        }

        void commit(Object tx) throws InvocationTargetException, IllegalAccessException {
            commit.invoke(tx);
        }

        void rollback(Object tx) throws InvocationTargetException, IllegalAccessException {
            rollback.invoke(tx);
        }
    }
}
