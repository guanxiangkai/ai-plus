dependencies {
    api(projects.webPlusCore)
    api(libs.fastexcel)
    implementation(libs.poi.ooxml)
    implementation(libs.poi.scratchpad)
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.context)
    testImplementation(libs.bundles.excel.compileOnly)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
