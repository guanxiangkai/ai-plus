/**
 * redis-plus-cache-starter - cache auto-configuration.
 */
dependencies {
    api(projects.redisPlusCache)
    api(projects.redisPlusCoreStarter)
    api(projects.redisPlusLockStarter)

    implementation(libs.bundles.starter.impl.support)
    compileOnly(libs.bundles.starter.compileOnly)

    annotationProcessor(libs.spring.boot.configuration.processor)
}
