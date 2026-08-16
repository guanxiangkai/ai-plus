dependencies {
    api(projects.webPlusCore)
    api(libs.fastexcel)
    implementation(libs.bundles.excel.implementation)
    compileOnly(libs.bundles.auto.configuration.compileOnly)
    testImplementation(libs.bundles.excel.compileOnly)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
