# Redis Plus 智能体指南

## Codex 强执行核心

- 先读本级/上级 `AGENTS.md`、README、构建文件和相关源码；用 `rg` 定位，不猜仓库事实。
- 先判影响面：模块、API/DTO、权限、缓存、事务、部署、文档、测试。
- 简单任务直接做；复杂/跨模块先给计划，列路径、风险、验证命令。
- 复用现有架构；清理重复、无效入口、临时代码，不为最小 diff 牺牲质量。
- 改代码同步类型、配置、测试、mock、文档、示例和调用方。
- 后端查鉴权、租户、事务、幂等、并发、分页、超时、日志脱敏；前端补齐 loading/empty/error/disabled/权限/窄屏。
- 依赖、构建和测试在 GitHub 托管的 Linux CI 环境执行；本地只保存和编辑源码。
- 交付说明改动、验证、风险；没跑的测试不能说通过。
- 重复错误写回 `AGENTS.md`；确定性规则沉淀为测试、lint、hook、CI 或 skill。

## 核心原则

- 这是独立发布的企业级 Redis 增强框架，基线为 Oracle GraalVM 25.0.4 + Spring Boot 4.1.0。
- 目标是保持模块化、可按需引入、可观测、可替换后端，不把业务系统逻辑写进框架。
- 普通模块暴露 Redis 能力 API、注解、SPI 和实现；`redis-plus-*-starter` 负责单能力自动装配，`redis-plus-starter` 负责聚合。
- 直接维护唯一有效的后端、API、SPI 和自动配置契约，不提供别名、双实现、静默降级或已弃用代理。调整契约时一次性同步所有调用方、文档、测试和示例。
- 用户用中文提问时，使用中文回复。

## 项目整体思维

- 必须把当前仓库放在所属产品和依赖体系里理解；这不是孤立功能的累加，而是由框架模块、消费方、发布契约、配置、文档和测试共同组成的整体。
- 新增或优化功能前，先判断它与现有模块、公开 API/SPI、自动配置、依赖基线、运行环境、文档示例和测试验证的关系，避免局部正确造成整体割裂。
- 设计方案要优先维护统一模型、统一扩展方式和统一消费体验；发现现有抽象不支撑整体演进时，应改进抽象，而不是堆叠旁路实现。

## 项目地图

- `redis-plus-core` / `redis-plus-core-starter`：Key 规范、序列化、TTL、事件、指标和基础自动配置。
- `redis-plus-lock` / `redis-plus-lock-starter`：Redisson 分布式锁、读写锁、WatchDog、锁 SPI 和切面。
- `redis-plus-datasource` / `redis-plus-datasource-starter`：多 Redis 数据源、路由连接工厂和上下文切换。
- `redis-plus-cache` / `redis-plus-cache-starter`：L1 Caffeine + L2 Redis + L3 回源保护的三级缓存。
- `redis-plus-enhance` / `redis-plus-enhance-starter`：布隆过滤器、批量缓存、防穿透/防雪崩策略。
- `redis-plus-ratelimit` / `redis-plus-ratelimit-starter`：固定窗口、滑动窗口、令牌桶、漏桶和限流算法 SPI。
- `redis-plus-idempotent` / `redis-plus-idempotent-starter`：幂等注解、幂等执行器和状态存储。
- `redis-plus-queue` / `redis-plus-queue-starter`：Redis List/Stream 队列、订阅运行时、ACK 和重试语义。
- `redis-plus-governance` / `redis-plus-governance-starter`：Micrometer 指标、Actuator 健康检查和治理扩展。
- `redis-plus-starter`：聚合全部单能力 starter。
- `buildSrc`：Maven Central POM 元数据约定。

## 发布版本与分支治理

- Redis Plus 位于 `ai-plus` monorepo，发布 Group 为 `io.github.guanxiangkai`，制品仓库为 Maven Central。
- 每个模块独立维护版本，版本唯一记录在 monorepo 根目录 `gradle/module-versions.properties`。
- 修改公共契约时，只提升发生变化的模块及受影响的下游模块，不得恢复能力族统一版本。
- 每次修改都必须先判断版本增量：
  - `PATCH`：兼容修复、文档/AGENTS/构建元数据调整、内部实现优化。
  - `MINOR`：兼容新增公开 API、配置项、模块能力、自动配置或扩展点。
  - `MAJOR`：破坏性 API/SPI、模块名、groupId/artifactId、JDK/Spring 基线或默认行为变化。
- 当前公开基线只维护受保护的 `main`；后续变更通过功能分支和 Pull Request 进入 `main`。

## 常用命令

- 查看模块：`./gradlew -p redis-plus projects`。
- 全量构建：`./gradlew buildAll`。
- 单模块测试：`./gradlew -p redis-plus :redis-plus-cache:test`。
- 发布前本地检查：`./gradlew publishToMavenLocalAll`。

## 架构边界

- 依赖版本集中维护在 `gradle/libs.versions.toml`。
- 新增或修改代码注释时默认使用中文，覆盖 Java/Kotlin 源码、Javadoc/KDoc、Gradle、TOML、YAML、Shell、Dockerfile 等源码和配置文件中的注释；只有专有名词、协议字段、第三方 API 名称、外部规范要求的文本可以保留英文。
- 修改 `gradle/libs.versions.toml` 时，新增或调整的版本、库、插件、bundle 分组都要配清晰中文注释，说明用途、适用模块和关键升级风险；公共框架、starter 聚合和破坏性版本升级尤其不能缺注释。
- 多个模块重复使用的依赖组合要优先封装到版本目录 `[bundles]` 中，并在模块侧使用 `implementation(libs.bundles.*)`、`api(libs.bundles.*)` 等方式引用；不要在多个 `build.gradle.kts` 中复制同一组依赖声明。
- `settings.gradle.kts` 使用显式模块清单；新增或删除模块时必须同步 README、版本目录、starter 聚合依赖和配置示例。
- L1 本地缓存使用 Caffeine 并明确 TTL，不使用无失效策略的 `ConcurrentHashMap` 实现缓存。
- 锁后端统一使用 Redisson，不维护 RedisTemplate/Lua 锁实现。
- `@RateLimit.algorithm` 是字符串扩展点，新增算法优先注册 `RateLimitAlgorithm`。
- 队列 API 区分 List 与 Stream 能力；`receive` 返回 `QueueDelivery`，`subscribe` 返回 `QueueSubscription`，订阅必须具备可停止语义。
- 可选能力通过 `@ConditionalOnClass`、`@ConditionalOnBean`、`@ConditionalOnProperty` 控制，缺少 Redisson、Micrometer、Actuator 等依赖时应自动退让。

## 代码约定

- Java 包名使用 `io.github.guanxiangkai.redis.plus...`。
- 公共 API 要稳定、语义明确，避免暴露内部实现类。
- Key、TTL、序列化、事件和指标优先复用 `redis-plus-core` 抽象。
- 单能力 starter 只装配本能力相关 Bean；聚合 starter 只传递依赖，不复制自动配置逻辑。
- 测试应覆盖并发、TTL、失效、重试、ACK、异常传播、缺依赖退让和用户自定义 Bean 覆盖场景。

## 验证

- Java 或 Gradle 变化后实际可行就运行 monorepo 根目录的 `./gradlew buildAll`。
- 锁、缓存、限流、幂等、队列、多数据源或治理变化必须运行对应模块测试。
- 发布相关变化至少运行 `./gradlew publishToMavenLocalAll`。
- 如果验证因为本机 JDK、Docker、Redis 或网络限制无法运行，最终回复明确说明。

## 完成标准

- 模块边界清晰，无重复实现、无效入口、临时代码、已弃用 API 和静默降级分支。
- 自动配置可按条件退让，用户自定义 Bean 能覆盖默认实现。
- 新增模块、配置项、导出入口或依赖时，版本目录、README、配置示例、starter 聚合和使用方同步更新。
