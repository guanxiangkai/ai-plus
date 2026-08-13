/**
 * redis-plus-cache-starter - cache auto-configuration.
 */
dependencies {
    api(projects.redisPlusCache)
    api(projects.redisPlusCoreStarter)
    api(projects.redisPlusLockStarter)

    implementation(libs.bundles.starter.impl.support)
    compileOnly(libs.jackson.databind)
    compileOnly(libs.jakarta.validation.api)

    annotationProcessor(libs.spring.boot.configuration.processor)
}
