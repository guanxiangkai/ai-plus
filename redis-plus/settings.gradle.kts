/**
 * Gradle 多项目构建配置
 *
 * 使用显式模块清单，避免配置阶段递归扫描仓库文件树。
 */

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
        val useAliyunMirror = providers.gradleProperty("useAliyunMirror")
            .map { it.equals("true", ignoreCase = true) }
            .orElse(false)

        if (useAliyunMirror.get()) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
            maven("https://maven.aliyun.com/repository/public")
        }
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = rootDir.name

include(
    "redis-plus-core",
    "redis-plus-core-starter",
    "redis-plus-lock",
    "redis-plus-lock-starter",
    "redis-plus-datasource",
    "redis-plus-datasource-starter",
    "redis-plus-cache",
    "redis-plus-cache-starter",
    "redis-plus-enhance",
    "redis-plus-enhance-starter",
    "redis-plus-ratelimit",
    "redis-plus-ratelimit-starter",
    "redis-plus-idempotent",
    "redis-plus-idempotent-starter",
    "redis-plus-queue",
    "redis-plus-queue-starter",
    "redis-plus-governance",
    "redis-plus-governance-starter",
    "redis-plus-starter",
)
