dependencies {
    implementation(project(":artifact-plus-core"))
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.core)
    // 仅作为独立 ProGuard 进程的工具类路径，不调用其 Java API。
    runtimeOnly(libs.proguard)
}

tasks.processResources {
    val moduleVersion = project.version.toString()
    val coreVersion = project(":artifact-plus-core").version.toString()
    val proguardVersion = libs.versions.proguard.get()
    inputs.property("moduleVersion", moduleVersion)
    inputs.property("coreVersion", coreVersion)
    inputs.property("proguardVersion", proguardVersion)
    filesMatching("META-INF/maven/plugin.xml") {
        // 只替换构建版本；保留 Maven 自己的 project / env / 用户属性表达式。
        filter { line: String -> line.replace("\${moduleVersion}", moduleVersion)
            .replace("\${coreVersion}", coreVersion)
            .replace("\${proguardVersion}", proguardVersion) }
    }
}

configure<PublishingExtension> {
    publications.named<MavenPublication>("mavenJava") { pom.packaging = "maven-plugin" }
}
