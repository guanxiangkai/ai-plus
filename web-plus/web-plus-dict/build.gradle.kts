dependencies {
    api(projects.webPlusCore)
    api(plusModule("redis-plus-starter"))
    compileOnly(libs.bundles.dict.compileOnly)
    testImplementation(libs.bundles.dict.compileOnly)
    testImplementation(plusModule("redis-plus-starter"))
    annotationProcessor(libs.spring.boot.configuration.processor)
}
