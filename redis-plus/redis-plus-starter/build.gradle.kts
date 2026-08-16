/**
 * redis-plus-starter — Spring Boot 自动装配与注解接入层
 *
 * 职责：自动装配全部模块 Bean、配置属性绑定、AOP 注解切面注册、
 *       健康检查与 Micrometer 指标注册
 */
dependencies {
    // 聚合所有能力 starter；单能力使用方可只引入 redis-plus-*-starter。
    api(projects.redisPlusCoreStarter)
    api(projects.redisPlusLockStarter)
    api(projects.redisPlusDatasourceStarter)
    api(projects.redisPlusCacheStarter)
    api(projects.redisPlusEnhanceStarter)
    api(projects.redisPlusRatelimitStarter)
    api(projects.redisPlusIdempotentStarter)
    api(projects.redisPlusQueueStarter)
    api(projects.redisPlusGovernanceStarter)

    // Starter 自身自动装配代码直接使用的运行时实现依赖
    implementation(libs.bundles.starter.impl.support)

    // Jackson 序列化 Bean 与配置属性校验只作为 Starter 编译契约，由最终应用提供运行时实现。
    compileOnly(libs.bundles.starter.compileOnly)

    // 配置元数据处理器（生成 IDE 配置提示）
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.bundles.starter.testing)
}
