# AI Plus

AI Plus 是 JPA Plus、Redis Plus 与 Web Plus 的 Java monorepo。三个能力族共享一个 Git 仓库和质量门禁，并保留清晰的模块边界；39 个 Maven 模块独立维护版本。

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
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.guanxiangkai:jpa-plus-starter:<version>")
    implementation("io.github.guanxiangkai:redis-plus-starter:<version>")
    implementation("io.github.guanxiangkai:web-plus-starter:<version>")
}
```

Maven Central 的公开制品无需读取凭据。版本以本仓库的模块版本文件和 Central 实际可用坐标为准。

## 构建与验证

依赖、构建和测试在 GitHub 托管的 Linux CI 环境执行：

```bash
./gradlew buildAll --no-daemon
./gradlew publishToMavenLocalAll --no-daemon
```

## 发布

提交并晋级到 `main` 后，可在 GitHub Actions 手工运行“发布 Maven 模块”并输入模块清单；`v*` 标签会发布全部模块。实际发布版本以 `gradle/module-versions.properties` 为准。

发布通过 Central Portal 完成，并使用 GitHub Actions Secret 注入短期 Maven Central 用户令牌与内存 GPG 私钥。仓库、日志和构建产物不保存这些凭据。
