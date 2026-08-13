/**
 * redis-plus-enhance-starter - Bloom filter and batch cache auto-configuration.
 */
dependencies {
    api(projects.redisPlusEnhance)
    api(projects.redisPlusCacheStarter)
    // 自动配置直接使用 RedisScriptExecutor，显式锁定核心 starter 运行时契约。
    api(projects.redisPlusCoreStarter)

    implementation(libs.bundles.starter.impl.support)
    compileOnly(libs.jakarta.validation.api)

    annotationProcessor(libs.spring.boot.configuration.processor)
}
