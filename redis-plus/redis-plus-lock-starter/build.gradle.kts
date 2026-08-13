/**
 * redis-plus-lock-starter - distributed lock auto-configuration.
 */
dependencies {
    api(projects.redisPlusLock)
    api(projects.redisPlusCoreStarter)

    implementation(libs.bundles.starter.impl.support)
    implementation(libs.redisson)

    compileOnly(libs.jakarta.validation.api)

    annotationProcessor(libs.spring.boot.configuration.processor)
}
