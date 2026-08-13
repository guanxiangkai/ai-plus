/**
 * Gradle 多项目构建配置
 *
 * 2.0 起使用显式模块清单，避免配置阶段递归扫描仓库文件树。
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
    "jpa-plus-core",
    "jpa-plus-query",
    "jpa-plus-field",
    "jpa-plus-interceptor",
    "jpa-plus-audit",
    "jpa-plus-datasource",
    "jpa-plus-sharding",
    "jpa-plus-starter",
)
