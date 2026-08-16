plugins {
    base
}

val moduleBuilds = linkedMapOf(
    "jpa-plus-core" to "jpa-plus",
    "jpa-plus-query" to "jpa-plus",
    "jpa-plus-field" to "jpa-plus",
    "jpa-plus-interceptor" to "jpa-plus",
    "jpa-plus-audit" to "jpa-plus",
    "jpa-plus-datasource" to "jpa-plus",
    "jpa-plus-sharding" to "jpa-plus",
    "jpa-plus-starter" to "jpa-plus",
    "redis-plus-core" to "redis-plus",
    "redis-plus-core-starter" to "redis-plus",
    "redis-plus-lock" to "redis-plus",
    "redis-plus-lock-starter" to "redis-plus",
    "redis-plus-datasource" to "redis-plus",
    "redis-plus-datasource-starter" to "redis-plus",
    "redis-plus-cache" to "redis-plus",
    "redis-plus-cache-starter" to "redis-plus",
    "redis-plus-enhance" to "redis-plus",
    "redis-plus-enhance-starter" to "redis-plus",
    "redis-plus-ratelimit" to "redis-plus",
    "redis-plus-ratelimit-starter" to "redis-plus",
    "redis-plus-idempotent" to "redis-plus",
    "redis-plus-idempotent-starter" to "redis-plus",
    "redis-plus-queue" to "redis-plus",
    "redis-plus-queue-starter" to "redis-plus",
    "redis-plus-governance" to "redis-plus",
    "redis-plus-governance-starter" to "redis-plus",
    "redis-plus-starter" to "redis-plus",
    "web-plus-core" to "web-plus",
    "web-plus-error" to "web-plus",
    "web-plus-web" to "web-plus",
    "web-plus-security" to "web-plus",
    "web-plus-protection" to "web-plus",
    "web-plus-log" to "web-plus",
    "web-plus-doc" to "web-plus",
    "web-plus-excel" to "web-plus",
    "web-plus-dict" to "web-plus",
    "web-plus-mq" to "web-plus",
    "web-plus-job" to "web-plus",
    "web-plus-starter" to "web-plus",
)

val buildAll = tasks.register("buildAll") {
    group = "build"
    description = "构建并测试全部能力族和模块"
    dependsOn(
        gradle.includedBuild("jpa-plus").task(":buildAll"),
        gradle.includedBuild("redis-plus").task(":buildAll"),
        gradle.includedBuild("web-plus").task(":buildAll"),
    )
}

tasks.named("build") {
    dependsOn(buildAll)
}

tasks.named("clean") {
    dependsOn(
        gradle.includedBuild("jpa-plus").task(":cleanAll"),
        gradle.includedBuild("redis-plus").task(":cleanAll"),
        gradle.includedBuild("web-plus").task(":cleanAll"),
    )
}

tasks.register("publishToMavenLocalAll") {
    group = "publishing"
    description = "将全部独立模块发布到 Maven Local，用于校验 POM 与制品"
    dependsOn(
        gradle.includedBuild("jpa-plus").task(":publishToMavenLocalAll"),
        gradle.includedBuild("redis-plus").task(":publishToMavenLocalAll"),
        gradle.includedBuild("web-plus").task(":publishToMavenLocalAll"),
    )
}

val requestedModules = providers.gradleProperty("releaseModules").orNull
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?.let { requested -> if (requested == listOf("all")) moduleBuilds.keys.toList() else requested.distinct() }
    .orEmpty()

val unknownModules = requestedModules.filterNot(moduleBuilds::containsKey)
require(unknownModules.isEmpty()) {
    "releaseModules 包含未知模块: ${unknownModules.joinToString()}"
}

tasks.register("publishSelected") {
    group = "publishing"
    description = "将 releaseModules 指定的独立模块发布到 GitHub Packages"

    if (requestedModules.isEmpty()) {
        doFirst {
            error("必须使用 -PreleaseModules=模块名[,模块名] 或 -PreleaseModules=all 指定发布范围")
        }
    } else {
        dependsOn(requestedModules.map { module ->
            val buildName = moduleBuilds.getValue(module)
            gradle.includedBuild(buildName)
                .task(":$module:publishMavenJavaPublicationToGitHubPackagesRepository")
        })
    }
}

tasks.register("publishSelectedToCentral") {
    group = "publishing"
    description = "将 releaseModules 指定的独立模块签名并上传到 Maven Central 暂存区"

    if (requestedModules.isEmpty()) {
        doFirst {
            error("必须使用 -PreleaseModules=模块名[,模块名] 或 -PreleaseModules=all 指定发布范围")
        }
    } else {
        dependsOn(requestedModules.map { module ->
            val buildName = moduleBuilds.getValue(module)
            gradle.includedBuild(buildName)
                .task(":$module:publishMavenJavaPublicationToCentralRepository")
        })
    }
}
