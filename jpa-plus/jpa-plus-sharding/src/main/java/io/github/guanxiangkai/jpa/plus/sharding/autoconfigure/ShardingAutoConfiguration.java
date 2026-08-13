package io.github.guanxiangkai.jpa.plus.sharding.autoconfigure;

import io.github.guanxiangkai.jpa.plus.datasource.context.JpaPlusContext;
import io.github.guanxiangkai.jpa.plus.sharding.aop.ShardingQueryAspect;
import io.github.guanxiangkai.jpa.plus.sharding.interceptor.ShardingInterceptor;
import io.github.guanxiangkai.jpa.plus.sharding.router.DefaultShardingRouter;
import io.github.guanxiangkai.jpa.plus.sharding.router.ShardingRouter;
import io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRule;
import io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRuleRegistry;
import io.github.guanxiangkai.jpa.plus.sharding.spi.CrossShardQueryExecutor;
import io.github.guanxiangkai.jpa.plus.sharding.spi.ShardingAlgorithm;
import io.github.guanxiangkai.jpa.plus.sharding.spi.ShardingKeyExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 分库分表模块自动装配
 *
 * <p>条件装配：</p>
 * <ul>
 *   <li>{@code jpa-plus.sharding.enabled=true}（默认，可关闭）</li>
 *   <li>classpath 中存在 {@link JpaPlusContext}（依赖 jpa-plus-datasource）</li>
 * </ul>
 * <p><b>边界说明：</b>自 1.0 起，本自动装配类的类路径发现统一由 {@code jpa-plus-starter}
 * 托管；若业务方直接引入 {@code jpa-plus-sharding} 而不使用 starter，需要显式
 * {@code @Import(ShardingAutoConfiguration.class)}。</p>
 *
 * <h3>注册的 Bean</h3>
 * <ol>
 *   <li>{@link ShardingRuleRegistry} —— 规则注册表，读取配置文件中的 {@code jpa-plus.sharding.rules}</li>
 *   <li>{@link ShardingKeyExtractor} —— 默认使用 {@link ShardingKeyExtractor.AnnotationShardingKeyExtractor}</li>
 *   <li>{@link ShardingAlgorithm}    —— 默认使用 {@link ShardingAlgorithm.HashModShardingAlgorithm}</li>
 *   <li>{@link ShardingRouter}       —— 路由门面（{@link DefaultShardingRouter}）</li>
 *   <li>{@link ShardingInterceptor}  —— DataInterceptor（BEFORE），挂入拦截器链</li>
 * </ol>
 *
 * <h3>跨分片策略启动日志</h3>
 * <p>若配置了 {@code cross-shard-policy=BEST_EFFORT} 或 {@code SEATA}，
 * 启动时会打印 WARN / INFO 提示，帮助用户了解当前事务边界。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "jpa-plus.sharding", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(JpaPlusContext.class)
@EnableConfigurationProperties(ShardingProperties.class)
@EnableAspectJAutoProxy
public class ShardingAutoConfiguration {

    private static void logCrossShardPolicy(ShardingProperties.CrossShardPolicy policy) {
        switch (policy) {
            case REJECT -> log.info("[jpa-plus-sharding] Cross-shard policy: REJECT — single-shard writes only. " +
                    "Split requests by sharding key in the application layer for multi-shard scenarios.");
            case BEST_EFFORT -> log.warn("[jpa-plus-sharding] Cross-shard policy: BEST_EFFORT — " +
                    "no distributed transaction guarantee. Data inconsistency is possible on partial failure.");
            case SEATA -> log.info("[jpa-plus-sharding] Cross-shard policy: SEATA — " +
                    "ensure seata-spring-boot-starter is on the classpath and Seata server is configured.");
        }
    }

    /**
     * 分片规则注册表
     *
     * <p>读取 {@code jpa-plus.sharding.rules} 配置，批量注册分片规则。
     * 用户也可在启动后通过注入 {@link ShardingRuleRegistry} Bean 动态追加规则。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public ShardingRuleRegistry shardingRuleRegistry(ShardingProperties properties) {
        ShardingRuleRegistry registry = new ShardingRuleRegistry();
        if (properties.getRules() != null) {
            for (ShardingProperties.RuleConfig cfg : properties.getRules()) {
                ShardingRule rule = new ShardingRule(
                        cfg.getLogicTableName(),
                        cfg.getDbCount(),
                        cfg.getTableCount(),
                        cfg.getDbPattern() != null ? cfg.getDbPattern()
                                : cfg.getLogicTableName() + "_db_{index}",
                        cfg.getTablePattern() != null ? cfg.getTablePattern()
                                : cfg.getLogicTableName() + "_{index}",
                        cfg.getShardingKeyField()
                );
                registry.register(rule);
                log.info("[jpa-plus-sharding] Registered rule: table='{}', {}db×{}table, db='{}', table='{}'",
                        rule.logicTableName(), rule.dbCount(), rule.tableCount(),
                        rule.dbPattern(), rule.tablePattern());
            }
        }

        // 跨分片策略日志提示
        logCrossShardPolicy(properties.getCrossShardPolicy());

        return registry;
    }

    /**
     * 分片键提取器（默认基于 @Sharding 注解）
     */
    @Bean
    @ConditionalOnMissingBean
    public ShardingKeyExtractor shardingKeyExtractor() {
        return new ShardingKeyExtractor.AnnotationShardingKeyExtractor();
    }

    /**
     * 分片算法（默认 Hash-Mod 均匀分片）
     */
    @Bean
    @ConditionalOnMissingBean
    public ShardingAlgorithm shardingAlgorithm() {
        return new ShardingAlgorithm.HashModShardingAlgorithm();
    }

    /**
     * 跨分片查询执行器。
     *
     * <p>默认使用受配置约束的虚拟线程批次并行执行。业务方声明自己的
     * {@link CrossShardQueryExecutor} Bean 后，可完整替换该策略。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public CrossShardQueryExecutor crossShardQueryExecutor(ShardingProperties properties) {
        return new CrossShardQueryExecutor.ParallelCrossShardQueryExecutor(
                properties.getCrossShardQueryMaxConcurrency());
    }

    /**
     * 分片路由门面
     */
    @Bean
    @ConditionalOnMissingBean
    public ShardingRouter shardingRouter(ShardingRuleRegistry registry,
                                         ShardingKeyExtractor keyExtractor,
                                         ShardingAlgorithm algorithm) {
        return new DefaultShardingRouter(registry, keyExtractor, algorithm);
    }

    /**
     * 分库分表前置拦截器（挂入 DataInterceptor 责任链）
     */
    @Bean
    @ConditionalOnMissingBean
    public ShardingInterceptor shardingInterceptor(ShardingRouter router) {
        return new ShardingInterceptor(router);
    }

    // ─── 内部工具 ───

    /**
     * {@code @ShardingQuery} SpEL 路由切面
     *
     * <p>拦截 Repository 方法上的 {@link io.github.guanxiangkai.jpa.plus.sharding.annotation.ShardingQuery}
     * 注解，通过 SpEL 解析分片键表达式并自动路由到目标数据源。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public ShardingQueryAspect shardingQueryAspect(ShardingRouter router) {
        return new ShardingQueryAspect(router);
    }
}
