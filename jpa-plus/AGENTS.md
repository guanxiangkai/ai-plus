# JPA Plus 智能体指南

## Codex 强执行核心

- 先读本级/上级 `AGENTS.md`、README、构建文件和相关源码；用 `rg` 定位，不猜仓库事实。
- 先判影响面：模块、API/DTO、权限、缓存、事务、部署、文档、测试。
- 简单任务直接做；复杂/跨模块先给计划，列路径、风险、验证命令。
- 复用现有架构；清理重复、无效入口、临时代码，不为最小 diff 牺牲质量。
- 改代码同步类型、配置、测试、mock、文档、示例和调用方。
- 后端查鉴权、租户、事务、幂等、并发、分页、超时、日志脱敏；前端补齐 loading/empty/error/disabled/权限/窄屏。
- 依赖、构建、测试、运行都在满足项目版本基线的 Linux 环境执行。
- 交付说明改动、验证、风险；没跑的测试不能说通过。
- 重复错误写回 `AGENTS.md`；确定性规则沉淀为测试、lint、hook、CI 或 skill。

## 核心原则

- 这是独立发布的企业级 JPA 增强框架，基线为 Oracle GraalVM 25.0.4 + Spring Boot 4.1.0。
- 目标是保持轻量、模块化、可按需引入，不改变标准 JPA / Spring Data JPA 的使用习惯。
- 普通模块暴露查询、字段治理、拦截器、审计、多数据源、分片等能力；`jpa-plus-starter` 负责聚合和自动装配。
- 直接维护唯一有效的 API、SPI、构造器和自动配置契约，不提供别名、双实现、静默降级或已弃用代理。调整契约时一次性同步所有调用方、文档、测试和示例。
- 用户用中文提问时，使用中文回复。

## 项目整体思维

- 必须把当前仓库放在所属产品和依赖体系里理解；这不是孤立功能的累加，而是由框架模块、消费方、发布契约、配置、文档和测试共同组成的整体。
- 新增或优化功能前，先判断它与现有模块、公开 API/SPI、自动配置、依赖基线、运行环境、文档示例和测试验证的关系，避免局部正确造成整体割裂。
- 设计方案要优先维护统一模型、统一扩展方式和统一消费体验；发现现有抽象不支撑整体演进时，应改进抽象，而不是堆叠旁路实现。

## 项目地图

- `jpa-plus-core`：基础异常、模型、上下文、拦截器链和公共 SPI。
- `jpa-plus-query`：Lambda DSL、QueryWrapper、Join、分页、SQL AST 和执行器。
- `jpa-plus-field`：字段自动填充、加密、脱敏、字典、敏感词和字段处理 SPI。
- `jpa-plus-interceptor`：逻辑删除、乐观锁、多租户、数据权限、自动排序等数据拦截能力。
- `jpa-plus-audit`：审计快照、字段差异、事件发布和审计 SPI。
- `jpa-plus-datasource`：多数据源、动态路由、连接池扩展和数据源健康能力。
- `jpa-plus-sharding`：分库分表、分片路由、跨分片查询和分片事务边界。
- `jpa-plus-starter`：Spring Boot 聚合入口和自动配置。
- `buildSrc`：公共 Maven POM 元数据约定。

## 发布版本与分支治理

- JPA Plus 位于 `ai-plus` monorepo，发布 Group 为 `io.github.guanxiangkai`，公共制品发布到 Maven Central。
- 每个模块独立维护版本，版本唯一记录在 monorepo 根目录 `gradle/module-versions.properties`。
- 修改公共契约时，只提升发生变化的模块及受影响的下游模块，不得恢复能力族统一版本。
- 每次修改都必须先判断版本增量：
  - `PATCH`：兼容修复、文档/AGENTS/构建元数据调整、内部实现优化。
  - `MINOR`：兼容新增公开 API、配置项、模块能力、自动配置或扩展点。
  - `MAJOR`：破坏性 API/SPI、模块名、groupId/artifactId、JDK/Spring 基线或默认行为变化。
- 分支按 monorepo 的 `feature/* -> dev -> test -> main` 统一晋级。

## 常用命令

- 查看模块：`./gradlew -p jpa-plus projects`。
- 全量构建：`./gradlew buildAll`。
- 单模块测试：`./gradlew -p jpa-plus :jpa-plus-query:test`。
- 发布前本地检查：`./gradlew publishToMavenLocalAll`。
- 依赖更新检查：`./gradlew -p jpa-plus dependencyUpdates --no-parallel --no-configuration-cache`。

## 架构边界

- 依赖版本集中维护在 `gradle/libs.versions.toml`。
- 新增或修改代码注释时默认使用中文，覆盖 Java/Kotlin 源码、Javadoc/KDoc、Gradle、TOML、YAML、Shell、Dockerfile 等源码和配置文件中的注释；只有专有名词、协议字段、第三方 API 名称、外部规范要求的文本可以保留英文。
- 修改 `gradle/libs.versions.toml` 时，新增或调整的版本、库、插件、bundle 分组都要配清晰中文注释，说明用途、适用模块和关键升级风险；公共框架、starter 聚合和破坏性版本升级尤其不能缺注释。
- 多个模块重复使用的依赖组合要优先封装到版本目录 `[bundles]` 中，并在模块侧使用 `implementation(libs.bundles.*)`、`api(libs.bundles.*)` 等方式引用；不要在多个 `build.gradle.kts` 中复制同一组依赖声明。
- `settings.gradle.kts` 使用显式模块清单；新增模块必须同步 README、版本目录、starter 依赖和发布元数据。
- 自动装配统一由 `jpa-plus-starter` 托管；单独引入能力模块只获得 API/SPI 和能力实现，不应意外激活整套 Boot 自动配置。
- 扩展优先使用 Spring Bean，非 Spring 场景使用 JDK `ServiceLoader`；不定义额外的私有 SPI 描述符。
- `@Encrypt` 默认算法为 `AES_CBC`，不要重新引入 DES / 3DES 内置枚举。
- 框架模块不绑定具体数据库驱动、连接池、日志实现或业务用户上下文；这些由最终应用或 starter 条件装配提供。

## 代码约定

- Java 包名使用 `io.github.guanxiangkai.jpa.plus...`。
- 公共 API 要稳定、语义明确，避免暴露内部实现类。
- 破坏性 API 调整必须同步 README、配置示例和测试。
- Query、Interceptor、Field、Audit、Datasource、Sharding 之间通过核心抽象通信，避免互相穿透内部包。
- 测试应覆盖不同数据库方言、条件组合、空值、批量操作、事务边界和扩展 SPI。

## 验证

- Java 或 Gradle 变化后实际可行就运行 monorepo 根目录的 `./gradlew buildAll`。
- 查询 DSL、SQL 编译、拦截器链、字段处理、审计、多数据源或分片变化必须运行对应模块测试。
- 发布相关变化至少运行 `./gradlew publishToMavenLocalAll`。
- 如果验证因为本机 JDK 或网络限制无法运行，最终回复明确说明。

## 完成标准

- 模块边界清晰，无重复实现、旧入口、临时代码和无效兼容层。
- 自动配置可按条件退让，用户自定义 Bean 能覆盖默认实现。
- 新增模块、配置项、导出入口或依赖时，版本目录、README、配置示例和使用方同步更新。
