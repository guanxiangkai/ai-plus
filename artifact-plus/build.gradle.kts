import java.util.Properties
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    base
    alias(libs.plugins.maven.publish) apply false
}

val moduleVersions = Properties().apply {
    rootProject.file("../gradle/module-versions.properties").inputStream().use(::load)
}
val junitBom = libs.junit.bom
val junitJupiter = libs.junit.jupiter
val junitLauncher = libs.junit.launcher

subprojects {
    group = "io.github.guanxiangkai"
    version = requireNotNull(moduleVersions.getProperty(name)) { "缺少模块版本: $name" }
    apply(plugin = "java-library")
    apply(plugin = "com.vanniktech.maven.publish.base")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
            vendor.set(JvmVendorSpec.ORACLE)
        }
        withSourcesJar()
        withJavadocJar()
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(25)
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:deprecation", "-Werror"))
    }
    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).encoding = "UTF-8"
    }
    dependencies {
        "testImplementation"(platform(junitBom))
        "testImplementation"(junitJupiter)
        "testRuntimeOnly"(junitLauncher)
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("failed", "skipped") }
    }
    configure<PublishingExtension> {
        publications.create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set(project.name)
                description.set("AI Plus 业务制品保护构建工具")
                url.set("https://github.com/guanxiangkai/ai-plus")
                licenses { license {
                    name.set("Apache License 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                } }
                developers { developer {
                    id.set("guanxiangkai")
                    name.set("guanxiangkai")
                } }
                scm {
                    url.set("https://github.com/guanxiangkai/ai-plus")
                    connection.set("scm:git:git://github.com/guanxiangkai/ai-plus.git")
                    developerConnection.set("scm:git:ssh://github.com/guanxiangkai/ai-plus.git")
                }
            }
        }
    }
    configure<MavenPublishBaseExtension> {
        publishToMavenCentral(automaticRelease = true)
        signAllPublications()
    }
}

tasks.register("buildAll") { dependsOn(subprojects.map { it.tasks.named("build") }) }
tasks.register("cleanAll") { dependsOn(subprojects.map { it.tasks.named("clean") }) }
tasks.register("publishToMavenLocalAll") {
    dependsOn(subprojects.map { it.tasks.named("publishToMavenLocal") })
}
