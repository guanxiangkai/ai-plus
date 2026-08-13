# Web Plus 智能体指南

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

- 这是独立的 Spring Boot 4 / WebFlux 基础框架项目，不属于任何具体业务系统。
- 目标是像 `jpa-plus`、`redis-plus` 一样形成可发布、可按需引入、边界清晰的模块化框架。
- 对外集成契约是 Maven Central 坐标 `io.github.guanxiangkai:web-plus-*`；联合开发使用 Gradle composite build 将同坐标替换为本仓源码。
- 普通模块暴露 API、注解、SPI、基础模型和可复用类型；starter 负责自动装配、条件 Bean、运行时过滤器和切面。
- 不把任何业务系统的私有语义、数据库表、配置中心数据、服务名、租户规则或具体接口调用沉淀进本项目。
- 直接维护唯一有效契约，不提供别名、双实现、静默降级或已弃用代理。调整契约时一次性同步所有调用方、文档、测试和示例。
- 用户用中文提问时，使用中文回复。

## 项目整体思维

- 必须把当前仓库放在所属产品和依赖体系里理解；这不是孤立功能的累加，而是由框架模块、消费方、发布契约、配置、文档和测试共同组成的整体。
- 新增或优化功能前，先判断它与现有模块、公开 API/SPI、自动配置、依赖基线、运行环境、文档示例和测试验证的关系，避免局部正确造成整体割裂。
- 设计方案要优先维护统一模型、统一扩展方式和统一消费体验；发现现有抽象不支撑整体演进时，应改进抽象，而不是堆叠旁路实现。

## 项目地图

- `web-plus-core`：通用模型、常量、基础 SPI、响应结构和工具。
- `web-plus-error`：统一异常、错误码、全局异常处理和错误码文档贡献。
- `web-plus-web`：WebFlux 基础能力、Controller/Service/Repository 基类、JPA Plus 与 MapStruct Plus 集成。
- `web-plus-security`：当前用户上下文、安全过滤器、鉴权注解、可信转发和 Token 抽象。
- `web-plus-protection`：防重复提交、服务侧限流、防刷等接口保护能力。
- `web-plus-log`：访问日志、操作日志、登录日志、数据变更桥接和日志 SPI。
- `web-plus-doc`：SpringDoc / OpenAPI 文档增强。
- `web-plus-excel`：FastExcel 导入导出基础设施。
- `web-plus-dict`：基于 Redis Plus 三级缓存的字典翻译与刷新。
- `web-plus-mq`：Spring Cloud Stream 消息基础能力。
- `web-plus-job`：PowerJob Worker 公共处理器。
- `web-plus-starter`：聚合入口，传递全部能力模块。

## 发布版本与分支治理

- Web Plus 位于 `ai-plus` monorepo，发布 Group 为 `io.github.guanxiangkai`，制品仓库为 Maven Central。
- 每个模块独立维护版本，版本唯一记录在 monorepo 根目录 `gradle/module-versions.properties`。
- Web Plus 对 JPA Plus、Redis Plus 的版本直接读取同一权威版本文件；根 composite build 将 Maven 坐标替换为同仓源码。
- 每次修改都必须先判断版本增量：
  - `PATCH`：兼容修复、文档/AGENTS/构建元数据调整、内部实现优化。
  - `MINOR`：兼容新增公开 API、配置项、模块能力、自动配置或扩展点。
  - `MAJOR`：破坏性 API/SPI、模块名、groupId/artifactId、JDK/Spring 基线或默认行为变化。
- 当前公开基线只维护受保护的 `main`；后续变更通过功能分支和 Pull Request 进入 `main`。

## 常用命令

- 查看模块：`./gradlew -p web-plus projects`。
- 全量构建：`./gradlew buildAll`。
- 单模块测试：`./gradlew -p web-plus :web-plus-core:test`。
- 发布前本地检查：`./gradlew publishToMavenLocalAll`。
- 依赖更新检查：`./gradlew -p web-plus dependencyUpdates --no-parallel --no-configuration-cache`。

## 架构边界

- 依赖版本集中维护在 `gradle/libs.versions.toml`。
- 新增或修改代码注释时默认使用中文，覆盖 Java/Kotlin 源码、Javadoc/KDoc、Gradle、TOML、YAML、Shell、Dockerfile 等源码和配置文件中的注释；只有专有名词、协议字段、第三方 API 名称、外部规范要求的文本可以保留英文。
- 修改 `gradle/libs.versions.toml` 时，新增或调整的版本、库、插件、bundle 分组都要配清晰中文注释，说明用途、适用模块和关键升级风险；公共框架、starter 聚合和破坏性版本升级尤其不能缺注释。
- 多个模块重复使用的依赖组合要优先封装到版本目录 `[bundles]` 中，并在模块侧使用 `implementation(libs.bundles.*)`、`api(libs.bundles.*)` 等方式引用；不要在多个 `build.gradle.kts` 中复制同一组依赖声明。
- 根构建脚本使用显式模块清单；新增模块必须同步 `settings.gradle.kts`、版本目录、README 和本文件。
- `web-plus` 仓库内部模块之间可以使用 `project(":web-plus-*")` 参与同仓构建和发布 POM；这个约束不适用于消费方项目，消费方必须使用发布后的 Maven 坐标。
- Spring Boot 自动配置使用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。
- 可选能力通过 `@ConditionalOnClass`、`@ConditionalOnBean`、`@ConditionalOnProperty` 控制，不能因缺少 Redis、JPA、MQ、Excel、Doc 等依赖导致基础模块启动失败。
- 与 `jpa-plus`、`redis-plus` 集成时只依赖其公开 API，不复制其内部实现。
- WebFlux 是默认 Web 模型；不要引入 Servlet/Tomcat/MVC 过滤器作为核心实现。
- `web-plus-starter` 可以聚合能力，但不能成为唯一入口；单能力 starter 和普通模块必须保持可独立引入。

## 命名与文件组织

- Java 包名使用 `io.github.guanxiangkai.web.plus...`。
- 模块名使用 `web-plus-*`。
- 配置属性前缀使用 `web-plus.*`，不要沿用业务系统中的 `ai-common.*`。
- 自动配置类使用 `*AutoConfiguration` 后缀，属性类使用 `*Properties` 后缀。
- SPI 放在 `spi` 包，注解放在 `annotation` 包，模型放在 `model` 包，自动配置放在 `autoconfigure` 包。

## 验证

- Java 或 Gradle 变化后实际可行就运行 monorepo 根目录的 `./gradlew buildAll`。
- 自动配置变化至少补充或更新 `ApplicationContextRunner` 测试。
- 涉及 WebFilter、Reactor Context、ThreadLocal 传播、安全上下文时，应覆盖启用、禁用、缺依赖和自定义 Bean 覆盖场景。
- 如果验证因为本机 JDK 或网络限制无法运行，最终回复明确说明。

## 完成标准

- 模块边界清晰，无业务系统私有语义泄漏。
- 没有重复实现、无效入口、临时代码、已弃用 API 和静默降级分支。
- 新增模块、配置项、导出入口或依赖时，版本目录、README、AGENTS 和使用方同步更新。
