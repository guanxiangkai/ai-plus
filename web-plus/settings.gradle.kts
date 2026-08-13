/**
 * Gradle 多项目构建配置
 *
 * 使用显式模块清单，避免配置阶段递归扫描仓库文件树。
 */

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        val useAliyunMirror = providers.gradleProperty("useAliyunMirror")
            .map { it.equals("true", ignoreCase = true) }
            .orElse(false)

        if (useAliyunMirror.get()) {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val useMavenLocal = providers.gradleProperty("useMavenLocal")
            .map { it.equals("true", ignoreCase = true) }
            .orElse(false)
        val useAliyunMirror = providers.gradleProperty("useAliyunMirror")
            .map { it.equals("true", ignoreCase = true) }
            .orElse(false)

        if (useMavenLocal.get()) {
            mavenLocal()
        }
        if (useAliyunMirror.get()) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
            maven("https://maven.aliyun.com/repository/public")
        }
        mavenCentral()
    }
}

rootProject.name = rootDir.name

include(
    "web-plus-core",
    "web-plus-error",
    "web-plus-web",
    "web-plus-security",
    "web-plus-protection",
    "web-plus-log",
    "web-plus-doc",
    "web-plus-excel",
    "web-plus-dict",
    "web-plus-mq",
    "web-plus-job",
    "web-plus-starter",
)
