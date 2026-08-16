dependencies {
    api(projects.webPlusCore)
    api(libs.springdoc.openapi.starter.webflux.ui)
    compileOnly(libs.bundles.auto.configuration.compileOnly)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
