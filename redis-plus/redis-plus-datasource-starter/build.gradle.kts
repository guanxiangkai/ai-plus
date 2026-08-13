/**
 * redis-plus-datasource-starter - multi Redis datasource auto-configuration.
 */
dependencies {
    api(projects.redisPlusDatasource)
    api(projects.redisPlusCoreStarter)

    implementation(libs.bundles.starter.impl.support)

    compileOnly(libs.jakarta.validation.api)

    annotationProcessor(libs.spring.boot.configuration.processor)
}
