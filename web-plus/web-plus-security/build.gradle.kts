dependencies {
    api(projects.webPlusCore)
    api(libs.bundles.security.api)
    implementation(projects.webPlusError)
    implementation(libs.jjwt.api)
    compileOnly(libs.bundles.auth.compileOnly)
    testImplementation(libs.bundles.auth.compileOnly)
    runtimeOnly(libs.bundles.jjwt.runtime)
    compileOnly(libs.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
