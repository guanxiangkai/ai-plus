/**
 * redis-plus-queue-starter - queue auto-configuration.
 */
dependencies {
    api(projects.redisPlusQueue)
    api(projects.redisPlusCacheStarter)
    api(projects.redisPlusGovernanceStarter)
    // 自动配置直接使用 RedisScriptExecutor，显式锁定核心 starter 运行时契约。
    api(projects.redisPlusCoreStarter)

    implementation(libs.bundles.starter.impl.support)
    compileOnly(libs.jakarta.validation.api)

    annotationProcessor(libs.spring.boot.configuration.processor)
}
