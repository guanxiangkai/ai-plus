dependencies {
    api(libs.spring.cloud.starter.stream.kafka)
    api(libs.reactor.core.micrometer)
    implementation(projects.webPlusCore)
}
