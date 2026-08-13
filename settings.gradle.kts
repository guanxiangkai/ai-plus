/**
 * AI Plus 组合构建入口。
 *
 * 三个能力族保持独立的版本目录和构建逻辑，根构建负责统一编排，并用组合构建
 * 将 Web Plus 对 JPA Plus、Redis Plus 的 Maven 依赖替换为同仓源码模块。
 */

rootProject.name = "ai-plus"

val publicationGroup = "io.github.guanxiangkai"

includeBuild("jpa-plus") {
    dependencySubstitution {
        listOf(
            "jpa-plus-core",
            "jpa-plus-query",
            "jpa-plus-field",
            "jpa-plus-interceptor",
            "jpa-plus-audit",
            "jpa-plus-datasource",
            "jpa-plus-sharding",
            "jpa-plus-starter",
        ).forEach { module ->
            substitute(module("$publicationGroup:$module")).using(project(":$module"))
        }
    }
}

includeBuild("redis-plus") {
    dependencySubstitution {
        listOf(
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
        ).forEach { module ->
            substitute(module("$publicationGroup:$module")).using(project(":$module"))
        }
    }
}

includeBuild("web-plus")
