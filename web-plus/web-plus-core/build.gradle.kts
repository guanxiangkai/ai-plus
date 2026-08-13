dependencies {
    api(libs.bundles.core.api)
    api(plusModule("jpa-plus-field"))
    api(plusModule("jpa-plus-interceptor"))
    compileOnly(libs.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
