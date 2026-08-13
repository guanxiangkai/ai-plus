import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.process.CommandLineArgumentProvider
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import java.util.Properties

/**
 * 延迟解析 Mockito Java Agent，避免在配置阶段解析依赖并保持配置缓存可用。
 *
 * @property agentClasspath Mockito Agent 的单文件类路径
 */
class MockitoAgentArgumentProvider(
    @get:Classpath val agentClasspath: FileCollection,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf("-javaagent:${agentClasspath.singleFile.absolutePath}")
}

/**
 * 这是一个 Gradle 构建脚本，用于配置项目的构建过程。
 */
plugins {
    java
    alias(libs.plugins.lombok)
    alias(libs.plugins.springboot) apply false
    alias(libs.plugins.versions)
    alias(libs.plugins.maven.publish) apply false
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val stableRegex = "^[0-9,.v-]+(-r)?$".toRegex()
    return !stableKeyword && !stableRegex.matches(version)
}

val projectGroup = providers.gradleProperty("group").get()
val jdkRelease = providers.gradleProperty("jdk").map(String::toInt)
val projectEncoding = providers.gradleProperty("encoding")
val moduleVersions = Properties().apply {
    rootProject.file("../gradle/module-versions.properties").inputStream().use(::load)
}

allprojects {
    group = projectGroup

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(jdkRelease)
        options.encoding = projectEncoding.get()
        options.compilerArgs.add("-parameters")
    }
}

// ── 在 subprojects{} 中无法直接访问 libs，提前提取引用 ──
val lombokPlugin: Provider<PluginDependency> = libs.plugins.lombok
val lombokDependency: Provider<MinimalExternalModuleDependency> = libs.lombok
val springBootDependencies: Provider<MinimalExternalModuleDependency> = libs.spring.boot.dependencies
val slf4jApi: Provider<MinimalExternalModuleDependency> = libs.slf4j.api
val mockitoCore: Provider<MinimalExternalModuleDependency> = libs.mockito.core
val testingBundle: Provider<ExternalModuleDependencyBundle> = libs.bundles.testing

dependencies {
    compileOnly(lombokDependency)
    annotationProcessor(lombokDependency)
    "lombok"(lombokDependency)
}

// ╔═══════════════════════════════════════════════════════════════════════════════════════════════════╗
// ║                                   子模块公共配置                                                   ║
// ╚═══════════════════════════════════════════════════════════════════════════════════════════════════╝
subprojects {
    version = requireNotNull(moduleVersions.getProperty(name)) {
        "gradle/module-versions.properties 缺少模块版本: $name"
    }

    apply {
        plugin("java-library")
        plugin("com.vanniktech.maven.publish.base")
        plugin(lombokPlugin.get().pluginId)
    }

    val mockitoAgent = configurations.create("mockitoAgent")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(jdkRelease.map(JavaLanguageVersion::of))
            vendor.set(org.gradle.jvm.toolchain.JvmVendorSpec.ORACLE)
        }
        withSourcesJar()
        withJavadocJar()
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
                pom { configureJpaPlusPom(project, project.name) }
            }
        }
    }

    configure<MavenPublishBaseExtension> {
        publishToMavenCentral(automaticRelease = true)
        signAllPublications()
    }

    dependencies {
        "implementation"(platform(springBootDependencies.get()))
        "implementation"(slf4jApi)
        "compileOnly"(lombokDependency)
        "annotationProcessor"(lombokDependency)
        "lombok"(lombokDependency)
        testImplementation(testingBundle)
        testImplementation(mockitoCore)
        mockitoAgent(platform(springBootDependencies.get()))
        mockitoAgent(mockitoCore) { isTransitive = false }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        jvmArgs("-Xshare:off")
        jvmArgumentProviders.add(MockitoAgentArgumentProvider(mockitoAgent))
        testLogging { events("passed", "skipped", "failed") }
    }
}

tasks.register("buildAll") {
    group = "build"
    description = "构建并测试 JPA Plus 全部模块"
    dependsOn(subprojects.map { it.tasks.named("build") })
}

tasks.register("cleanAll") {
    group = "build"
    description = "清理 JPA Plus 全部模块"
    dependsOn(subprojects.map { it.tasks.named("clean") })
}

tasks.register("publishToMavenLocalAll") {
    group = "publishing"
    description = "将 JPA Plus 全部模块发布到 Maven Local"
    dependsOn(subprojects.map { it.tasks.named("publishToMavenLocal") })
}

tasks.named<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>("dependencyUpdates").configure {
    gradleReleaseChannel = "current"
    rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }
}
