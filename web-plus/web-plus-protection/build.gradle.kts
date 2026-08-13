dependencies {
    api(projects.webPlusCore)
    api(libs.bundles.protection.api)
    compileOnly(libs.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
