dependencies {
    api(projects.webPlusCore)
    api(libs.springdoc.openapi.starter.webflux.ui)
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.context)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
