/**
 * jpa-plus-sharding — 分库分表路由模块
 *
 * <p>提供轻量级分库分表路由能力，核心职责：</p>
 * <ul>
 *   <li>{@code @Sharding}             —— 标注实体中的分片键字段</li>
 *   <li>{@code ShardingRule}          —— 分片规则（逻辑表名 / 库数 / 表数 / 命名模式）</li>
 *   <li>{@code ShardingAlgorithm}     —— SPI：分片算法（默认 Hash-Mod，可替换）</li>
 *   <li>{@code ShardingKeyExtractor}  —— SPI：分片键提取器</li>
 *   <li>{@code ShardingRouter}        —— 路由门面，整合规则 + 算法 + 上下文</li>
 *   <li>{@code ShardingInterceptor}   —— DataInterceptor(BEFORE)，SAVE/DELETE 前写入路由上下文</li>
 *   <li>{@code ShardingContext}       —— 基于 ScopedValue 的当前分片目标上下文</li>
 * </ul>
 *
 * <p><b>跨分片事务边界：</b>当前版本仅支持单分片写入（cross-shard-policy = REJECT）。
 * 需要跨分片事务时，建议接入 Seata 或自行实现最终一致性方案。</p>
 *
 * <p><b>与多数据源的协作：</b>ShardingInterceptor 在 BEFORE 阶段设置 ShardingContext，
 * DynamicRoutingDataSource 通过 JpaPlusContext 读取路由 key，二者通过 ShardingContext 解耦。</p>
 *
 * <p><b>依赖边界：</b>模块公开暴露 core / datasource 契约；Spring / SpEL / AOP / Boot 自动装配
 * 仅作为内部运行时基础设施存在。自 1.0 起，类路径自动装配发现由 jpa-plus-starter 统一托管；
 * 直接使用本模块时如需 Boot 自动装配，请显式导入 {@code ShardingAutoConfiguration}。</p>
 */

dependencies {
    api(projects.jpaPlusCore)             // ShardingInterceptor 公共类型实现 DataInterceptor / DataInvocation 等 core 契约
    api(projects.jpaPlusDatasource)       // SeataShardingTransactionTemplate 公开使用 JpaPlusContext
    implementation(libs.bundles.sharding.internal)     // @AutoConfiguration / @Bean / SpEL / AspectJ 仅供模块内部实现
    implementation(libs.bundles.sharding.validation)  // 配置属性校验注解与运行时实现
    annotationProcessor(platform(libs.spring.boot.dependencies))
    annotationProcessor(libs.spring.boot.configuration.processor)

    // ── 测试 ──
    testImplementation(projects.jpaPlusDatasource)
}
