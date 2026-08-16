# AI Plus

AI Plus 是 JPA Plus、Redis Plus 与 Web Plus 的 Java monorepo。三个能力族共享一个 Git 仓库和质量门禁，并保留清晰的模块边界；39 个 Maven 模块独立维护版本。

本仓库使用 [Apache License 2.0](LICENSE)。各能力族目录保留同一许可证副本，发布的 Maven
模块沿用该许可；真实业务数据、部署凭据和第三方商标不属于授权范围。

## 仓库结构

| 目录 | 职责 | 模块数量 |
| --- | --- | ---: |
| `jpa-plus/` | JPA 查询、字段治理、拦截、审计、多数据源与分片 | 8 |
| `redis-plus/` | Redis 核心、锁、缓存、限流、幂等、队列与治理 | 19 |
| `web-plus/` | WebFlux、安全、防护、日志、文档、Excel、消息与任务 | 12 |

根 Gradle composite build 会把 Web Plus 使用的 JPA Plus、Redis Plus Maven 坐标替换为同仓源码模块，因此一次构建可以验证完整依赖图。

## 坐标与版本

- Group：`io.github.guanxiangkai`
- Registry：Maven Central
- 权威版本文件：`gradle/module-versions.properties`

例如：

```kotlin
repositories { mavenCentral() }

dependencies {
    implementation("io.github.guanxiangkai:jpa-plus-starter:<version>")
    implementation("io.github.guanxiangkai:redis-plus-starter:<version>")
    implementation("io.github.guanxiangkai:web-plus-starter:<version>")
}
```

公共消费方不需要仓库令牌。GitHub Packages 仍作为同仓的可选发布目标，其客户端认证要求以
GitHub 当前规则为准；任何凭据都只能放在用户级 Gradle 配置或 CI Secret 中。

## 构建与验证

依赖、构建和测试在满足版本基线的 Linux 环境执行：

```bash
./gradlew buildAll --no-daemon
./gradlew publishToMavenLocalAll --no-daemon
```

## 发布

提交并晋级到 `main` 后，在 GitHub Actions 手工运行“发布 Maven 模块”，选择 Maven Central
或 GitHub Packages 并输入模块清单；实际版本以 `gradle/module-versions.properties` 为准。
Central 首次发布应选择 `all`，使用 Actions Secrets 中的 Portal 用户令牌和内存 PGP 密钥签名。
`user_managed` 只提交 Portal 校验，`automatic` 会在校验通过后发布不可变制品。
