import java.util.Properties

plugins {
    java
    alias(libs.plugins.lombok)
    alias(libs.plugins.springboot) apply false
    alias(libs.plugins.versions)
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val stableRegex = "^[0-9,.v-]+(-r)?$".toRegex()
    return !stableKeyword && !stableRegex.matches(version)
}

val projectGroup = providers.gradleProperty("group").get()
val moduleVersions = Properties().apply {
    rootProject.file("../gradle/module-versions.properties").inputStream().use(::load)
}

allprojects {
    group = projectGroup

    val encoding = providers.gradleProperty("encoding")
    val jdkRelease = providers.gradleProperty("jdk").map(String::toInt)

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(jdkRelease)
        options.encoding = encoding.get()
        options.compilerArgs.add("-parameters")
    }
}

val lombokPlugin: Provider<PluginDependency> = libs.plugins.lombok
val lombokDependency: Provider<MinimalExternalModuleDependency> = libs.lombok
val springBootDependencies: Provider<MinimalExternalModuleDependency> = libs.spring.boot.dependencies
val springCloudDependencies: Provider<MinimalExternalModuleDependency> = libs.spring.cloud.dependencies
val jackson3Bom: Provider<MinimalExternalModuleDependency> = libs.jackson3.bom
val jackson2Bom: Provider<MinimalExternalModuleDependency> = libs.jackson2.bom
val nettyBom: Provider<MinimalExternalModuleDependency> = libs.netty.bom
val commonsIo: Provider<MinimalExternalModuleDependency> = libs.commons.io
val lz4Java: Provider<MinimalExternalModuleDependency> = libs.lz4.java
val slf4jApi: Provider<MinimalExternalModuleDependency> = libs.slf4j.api
val testingBundle: Provider<ExternalModuleDependencyBundle> = libs.bundles.testing
val junitPlatformLauncher: Provider<MinimalExternalModuleDependency> = libs.junit.platform.launcher
val configurationProcessor: Provider<MinimalExternalModuleDependency> = libs.spring.boot.configuration.processor

dependencies {
    compileOnly(lombokDependency)
    annotationProcessor(lombokDependency)
    "lombok"(lombokDependency)
}

subprojects {
    version = requireNotNull(moduleVersions.getProperty(name)) {
        "gradle/module-versions.properties 缺少模块版本: $name"
    }

    apply {
        plugin("java-library")
        plugin("maven-publish")
        plugin("signing")
        plugin(lombokPlugin.get().pluginId)
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(providers.gradleProperty("jdk").map { JavaLanguageVersion.of(it.toInt()) })
            vendor.set(org.gradle.jvm.toolchain.JvmVendorSpec.ORACLE)
        }
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<Javadoc>().configureEach {
        val projectJdk = providers.gradleProperty("jdk")
        val projectEncoding = providers.gradleProperty("encoding")
        options {
            this as StandardJavadocDocletOptions
            encoding = projectEncoding.get()
            addStringOption("source", projectJdk.get())
            addBooleanOption("Xdoclint:none", true)
        }
        isFailOnError = false
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                pom { configureWebPlusPom(project, project.name) }
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
        // 保持 Boot/Cloud 主版本不变，并把已确认有漏洞的基础库提升到兼容补丁版本。
        "api"(platform(jackson3Bom.get()))
        "api"(platform(jackson2Bom.get()))
        "api"(platform(nettyBom.get()))
        "api"(platform(springBootDependencies.get()))
        "api"(platform(springCloudDependencies.get()))
        "implementation"(platform(springBootDependencies.get()))
        "implementation"(platform(springCloudDependencies.get()))
        "compileOnly"(platform(springBootDependencies.get()))
        "compileOnly"(platform(springCloudDependencies.get()))
        "annotationProcessor"(platform(springBootDependencies.get()))
        "implementation"(slf4jApi)
        "compileOnly"(lombokDependency)
        "annotationProcessor"(lombokDependency)
        "annotationProcessor"(configurationProcessor)
        "lombok"(lombokDependency)
        constraints {
            "api"(commonsIo) {
                because("修复第三方传递的 Commons IO 高危漏洞")
            }
            "api"(lz4Java) {
                because("修复 Kafka 客户端传递的 LZ4 安全问题")
            }
        }
        testImplementation(testingBundle)
        testRuntimeOnly(junitPlatformLauncher)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        jvmArgs("-Xshare:off")
        testLogging { events("passed", "skipped", "failed") }
    }
}

tasks.register("buildAll") {
    group = "build"
    description = "构建并测试 Web Plus 全部模块"
    dependsOn(subprojects.map { it.tasks.named("build") })
}

tasks.register("cleanAll") {
    group = "build"
    description = "清理 Web Plus 全部模块"
    dependsOn(subprojects.map { it.tasks.named("clean") })
}

tasks.register("publishToMavenLocalAll") {
    group = "publishing"
    description = "将 Web Plus 全部模块发布到 Maven Local"
    dependsOn(subprojects.map { it.tasks.named("publishToMavenLocal") })
}

tasks.named<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>("dependencyUpdates").configure {
    gradleReleaseChannel = "current"
    rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }
}
