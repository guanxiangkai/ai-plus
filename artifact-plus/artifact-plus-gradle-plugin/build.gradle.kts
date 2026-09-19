import org.gradle.language.jvm.tasks.ProcessResources

plugins { `java-gradle-plugin` }

dependencies {
    implementation(project(":artifact-plus-core"))
    testImplementation(gradleTestKit())
}

gradlePlugin {
    // 使用已有 mavenJava 发布；通过 pluginManagement.useModule 解析插件坐标。
    isAutomatedPublishing = false
    plugins.create("artifactProtection") {
        id = "io.github.guanxiangkai.artifact-protection"
        implementationClass = "io.github.guanxiangkai.artifact.plus.gradle.ArtifactProtectionPlugin"
        displayName = "AI Plus 业务制品保护"
        description = "业务 JAR 的可选混淆、完整制品签名与可信公钥校验"
    }
}

val proguardVersion = libs.versions.proguard

tasks.named<ProcessResources>("processResources") {
    val resolvedProguardVersion = proguardVersion.get()
    inputs.property("proguardVersion", resolvedProguardVersion)
    filesMatching("META-INF/artifact-plus-tool.properties") {
        expand("proguardVersion" to resolvedProguardVersion)
    }
}
