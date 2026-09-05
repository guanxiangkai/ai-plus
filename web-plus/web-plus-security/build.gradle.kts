dependencies {
    api(projects.webPlusCore)
    api(libs.bundles.security.api)
    implementation(projects.webPlusError)
    implementation(libs.jjwt.api)
    compileOnly(libs.bundles.auth.compileOnly)
    testImplementation(libs.bundles.auth.compileOnly)
    // 验证响应式请求体取消时的缓冲区释放，版本由 Spring Boot BOM 管理。
    testImplementation("io.projectreactor:reactor-test")
    runtimeOnly(libs.bundles.jjwt.runtime)
    compileOnly(libs.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
