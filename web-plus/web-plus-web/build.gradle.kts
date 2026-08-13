dependencies {
    api(projects.webPlusCore)
    api(projects.webPlusSecurity)
    api(projects.webPlusLog)
    api(libs.bundles.web.api)
    api(plusModule("jpa-plus-starter"))
    compileOnly(libs.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
