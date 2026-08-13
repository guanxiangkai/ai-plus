dependencies {
    api(projects.webPlusCore)
    compileOnly(libs.bundles.log.compileOnly)
    compileOnly(plusModule("jpa-plus-core"))
    compileOnly(plusModule("jpa-plus-audit"))
    compileOnly(plusModule("redis-plus-starter"))
    testImplementation(libs.bundles.log.compileOnly)
    testImplementation(plusModule("jpa-plus-core"))
    testImplementation(plusModule("jpa-plus-audit"))
    testImplementation(plusModule("redis-plus-starter"))
    testImplementation(libs.spring.boot.starter.webflux)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
