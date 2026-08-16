import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import java.util.Properties

/**
 * 这是一个 Gradle 构建脚本，用于配置项目的构建过程。
 */
plugins {
    java
    alias(libs.plugins.lombok)
    alias(libs.plugins.springboot) apply false
    alias(libs.plugins.errorprone) apply false
}

val projectGroup = providers.gradleProperty("group").get()
val jdkRelease = providers.gradleProperty("jdk").map(String::toInt)
val projectEncoding = providers.gradleProperty("encoding")
val jdk25BuildJvmArgs = listOf("--sun-misc-unsafe-memory-access=allow")
val moduleVersions = Properties().apply {
    rootProject.file("../gradle/module-versions.properties").inputStream().use(::load)
}

allprojects {
    group = projectGroup
}

// ── 在 subprojects{} 中无法直接访问 libs，提前提取引用 ──
val lombokPlugin: Provider<PluginDependency> = libs.plugins.lombok
val springBootDependencies: Provider<MinimalExternalModuleDependency> = libs.spring.boot.dependencies
val jackson3Bom: Provider<MinimalExternalModuleDependency> = libs.jackson3.bom
val nettyBom: Provider<MinimalExternalModuleDependency> = libs.netty.bom
val slf4jApi: Provider<MinimalExternalModuleDependency> = libs.slf4j.api
val testingBundle: Provider<ExternalModuleDependencyBundle> = libs.bundles.testing
val junitPlatformLauncher: Provider<MinimalExternalModuleDependency> = libs.junit.platform.launcher
val errorProneCore: Provider<MinimalExternalModuleDependency> = libs.errorprone.core
val nullAway: Provider<MinimalExternalModuleDependency> = libs.nullaway

// ╔═══════════════════════════════════════════════════════════════════════════════════════════════════╗
// ║                                   子模块公共配置                                                   ║
// ╚═══════════════════════════════════════════════════════════════════════════════════════════════════╝
subprojects {
    version = requireNotNull(moduleVersions.getProperty(name)) {
        "gradle/module-versions.properties 缺少模块版本: $name"
    }

    apply {
        plugin("java-library")
        plugin("maven-publish")
        plugin("signing")
        plugin(lombokPlugin.get().pluginId)
        plugin("net.ltgt.errorprone")
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(jdkRelease.map(JavaLanguageVersion::of))
            vendor.set(org.gradle.jvm.toolchain.JvmVendorSpec.ORACLE)
        }
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.isFork = true
        options.forkOptions.jvmArgs?.addAll(jdk25BuildJvmArgs)
        options.release.set(jdkRelease)
        options.encoding = projectEncoding.get()
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:unchecked", "-Werror"))
        options.errorprone {
            check("NullAway", CheckSeverity.ERROR)
            option("NullAway:AnnotatedPackages", "io.github.guanxiangkai")
        }
    }

    tasks.withType<Javadoc>().configureEach {
        options {
            this as StandardJavadocDocletOptions
            encoding = projectEncoding.get()
            addStringOption("source", jdkRelease.get().toString())
            addBooleanOption("Xdoclint:none", true)
        }
        isFailOnError = false
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                pom { configureRedisPlusPom(project, project.name) }
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/guanxiangkai/ai-plus")
                credentials {
                    username = providers.gradleProperty("gpr.user").orNull
                        ?: System.getenv("GITHUB_ACTOR")
                    password = providers.gradleProperty("gpr.key").orNull
                        ?: System.getenv("GITHUB_TOKEN")
                }
            }
            maven {
                name = "Central"
                url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
                credentials {
                    username = providers.gradleProperty("centralUsername").orNull
                    password = providers.gradleProperty("centralPassword").orNull
                }
            }
        }
    }

    configure<org.gradle.plugins.signing.SigningExtension> {
        val signingKey = providers.gradleProperty("signingKey").orNull
        val signingPassword = providers.gradleProperty("signingPassword").orNull
        if (!signingKey.isNullOrBlank()) {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(extensions.getByType<PublishingExtension>().publications["mavenJava"])
        }
    }

    dependencies {
        // 对外发布同一兼容线的安全补丁约束，覆盖 Boot 基线中已确认存在漏洞的传递版本。
        "api"(platform(jackson3Bom.get()))
        "api"(platform(nettyBom.get()))
        "api"(platform(springBootDependencies.get()))
        "implementation"(platform(springBootDependencies.get()))
        "compileOnly"(platform(springBootDependencies.get()))
        "annotationProcessor"(platform(springBootDependencies.get()))
        testImplementation(platform(springBootDependencies.get()))
        "implementation"(slf4jApi)
        "errorprone"(errorProneCore)
        "errorprone"(nullAway)
        testImplementation(testingBundle)
        testRuntimeOnly(junitPlatformLauncher)
    }

    tasks.withType<JavaExec>().configureEach {
        jvmArgs(jdk25BuildJvmArgs)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        jvmArgs(jdk25BuildJvmArgs + listOf("-Xshare:off", "--enable-native-access=ALL-UNNAMED"))
        testLogging { events("passed", "skipped", "failed") }
    }
}

tasks.register("buildAll") {
    group = "build"
    description = "构建并测试 Redis Plus 全部模块"
    dependsOn(subprojects.map { it.tasks.named("build") })
}

tasks.register("cleanAll") {
    group = "build"
    description = "清理 Redis Plus 全部模块"
    dependsOn(subprojects.map { it.tasks.named("clean") })
}

tasks.register("publishToMavenLocalAll") {
    group = "publishing"
    description = "将 Redis Plus 全部模块发布到 Maven Local"
    dependsOn(subprojects.map { it.tasks.named("publishToMavenLocal") })
}
