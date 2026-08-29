import java.util.Properties
import com.vanniktech.maven.publish.MavenPublishBaseExtension

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
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:deprecation"))
    }
}

val lombokPlugin: Provider<PluginDependency> = libs.plugins.lombok
val lombokDependency: Provider<MinimalExternalModuleDependency> = libs.lombok
val springBootDependencies: Provider<MinimalExternalModuleDependency> = libs.spring.boot.dependencies
val springCloudDependencies: Provider<MinimalExternalModuleDependency> = libs.spring.cloud.dependencies
val slf4jApi: Provider<MinimalExternalModuleDependency> = libs.slf4j.api
val testingBundle: Provider<ExternalModuleDependencyBundle> = libs.bundles.testing
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
        plugin("com.vanniktech.maven.publish.base")
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
    }

    configure<MavenPublishBaseExtension> {
        publishToMavenCentral(automaticRelease = true)
        signAllPublications()
    }

    dependencies {
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
        testImplementation(testingBundle)
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
