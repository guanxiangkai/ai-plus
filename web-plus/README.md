# Web Plus

Web Plus 是面向 Spring Boot 4 / WebFlux 的企业级 Web 增强框架骨架，用于从业务系统中沉淀通用 Web、认证上下文、接口保护、日志、文档、Excel、MQ、任务等基础能力。

## 模块结构

| 模块 | 职责 |
| --- | --- |
| `web-plus-core` | 通用模型、常量、基础 SPI、响应结构、Reactor/异步上下文传播和工具 |
| `web-plus-error` | 统一异常、错误码、全局异常处理和错误码文档贡献 |
| `web-plus-web` | WebFlux 基础能力、Controller/Service/Repository 基类、JPA Plus/MapStruct Plus 集成、接口入参出参加密 |
| `web-plus-security` | 当前用户上下文、认证过滤器、鉴权注解、安全自动配置 |
| `web-plus-protection` | 防重复提交、服务侧限流、防刷等接口保护能力 |
| `web-plus-log` | HTTP TraceId、WebClient 透传、访问日志、操作日志、登录日志、数据变更桥接和日志 SPI |
| `web-plus-doc` | SpringDoc / OpenAPI 文档增强 |
| `web-plus-excel` | FastExcel 导入导出基础设施 |
| `web-plus-dict` | 基于 Redis Plus 三级缓存的字典翻译与刷新 |
| `web-plus-mq` | Spring Cloud Stream 消息、Observation 与消费线程 TraceId 恢复能力 |
| `web-plus-job` | PowerJob Worker 公共处理器 |
| `web-plus-starter` | 聚合入口，传递全部能力模块 |

## 设计原则

- 普通模块只暴露 API、注解、SPI 和可复用基础类型。
- 自动装配、过滤器、切面和运行时实现放入 starter 或各能力模块的自动配置中。
- 业务系统按需引入单能力模块；需要全量能力时引入 `web-plus-starter`。
- Web Plus 按 Maven Central 独立制品消费，消费方项目不要通过本地源码目录引入。
- 不把任何业务系统的私有语义、表结构、租户规则或具体服务调用写进 Web Plus。

## 获取依赖

### 环境要求

| 要求 | 最低版本 |
| --- | --- |
| JDK | Oracle GraalVM 25.0.4 |
| Spring Boot | 4.1.1+ |
| Gradle | 9.7.1+ |

### 推荐方式：Starter

```kotlin
dependencies {
    implementation("io.github.guanxiangkai:web-plus-starter:<version>")
}
```

### 按需引入

```kotlin
dependencies {
    implementation("io.github.guanxiangkai:web-plus-core:<version>")
    implementation("io.github.guanxiangkai:web-plus-security:<version>")
    implementation("io.github.guanxiangkai:web-plus-log:<version>")
}
```

### Version Catalog

```toml
[versions]
web-plus = "<version>"

[libraries]
web-plus-starter = { group = "io.github.guanxiangkai", name = "web-plus-starter", version.ref = "web-plus" }
web-plus-web = { group = "io.github.guanxiangkai", name = "web-plus-web", version.ref = "web-plus" }
web-plus-security = { group = "io.github.guanxiangkai", name = "web-plus-security", version.ref = "web-plus" }
web-plus-log = { group = "io.github.guanxiangkai", name = "web-plus-log", version.ref = "web-plus" }
```

## 构建

```bash
./gradlew build
```

基础配置示例见 `application-example.yml`。

## 全链路追踪

`web-plus-log` 默认从 Micrometer Tracing 当前 Span 获取标准 TraceId，并将同一个值写入
`X-Trace-Id` 请求头、响应头、Reactor Context 和 MDC。通过 Spring Boot 自动配置
`WebClient.Builder` 创建的客户端会同时获得标准 W3C `traceparent` 和 `X-Trace-Id` 透传；
不要使用 `WebClient.create()` 绕过自动配置构建器。

`web-plus-core` 使用 Micrometer Context Propagation 统一覆盖 Reactor 调度器和默认虚拟线程
执行器。自定义线程池应注入名为 `webPlusContextTaskDecorator` 的 `TaskDecorator`。
`web-plus-mq` 在消息头写入 `X-Trace-Id`，并在函数式消费者执行前恢复、执行后清理上下文。

业务应用需要标准 Span、W3C 传播和可视化后端时，应引入与项目 Spring Boot 版本一致的
`spring-boot-starter-opentelemetry`。OTLP 导出地址、采样率和启用开关属于部署配置，
不得硬编码在公共框架中。

## 接口加密

`web-plus-web` 内置 WebFlux 过滤器，可对标注了 `@ApiCrypto` 的接口执行 JSON 入参解密和 JSON 出参加密。未标注 `@ApiCrypto` 的接口保持普通明文接口行为。总开关默认关闭；启用后默认策略为 `SM4_CBC_SM3_V1`，请求和响应 keyId 分别为 `request-default`、`response-default`，应用应显式配置实际密钥与密钥标识。

```yaml
web-plus:
  api-crypto:
    enabled: false
    strategy: SM4_CBC_SM3_V1
    pbkdf2-iterations: 120000
    request:
      enabled: true
      key-id: request-default
      key: ${WEB_PLUS_API_REQUEST_CRYPTO_KEY:}
    response:
      enabled: true
      key-id: response-default
      key: ${WEB_PLUS_API_RESPONSE_CRYPTO_KEY:}
    runtime:
      max-request-body-size: 4MB
      max-response-body-size: 8MB
      max-query-envelope-length: 16384
      worker-count: 8
      task-queue-capacity: 1024
```

- `enabled` 是总开关，默认关闭；关闭时不要求配置密钥，也不会注册加解密过滤器。
- `request.enabled` 是入参解密总开关；只有同时命中 `@ApiCrypto(request = true)` 的接口才会解密，并拒绝明文 JSON body 和明文 query 参数，避免降级绕过。
- `response.enabled` 是出参加密总开关；只有同时命中 `@ApiCrypto(response = true)` 的接口才会加密 JSON 响应。标准 `ApiResponse` 只加密 `data` 字段，`code/message/timestamp` 等外层状态保持明文；`data` 为 `null` 时不写加密响应头。文件下载、SSE、表单等非 JSON 流量跳过。
- `runtime` 为请求、响应和查询信封设置明确聚合上限，并使用有界专用工作池执行 PBKDF2、JSON 编解码和对称加密；超过请求上限返回 413，超过响应上限按服务端错误快速失败。NDJSON、`stream+json`、SSE 和文件流保持流式传输，不参与聚合加密。
- 请求和响应密钥必须与相应客户端实现一致；框架不约束客户端环境变量名称。
- 前端启动时可读取 `/web-plus/api-crypto/config` 获取总开关、入参/出参开关、策略、keyId、请求头、查询参数名和 `@ApiCrypto` 端点规则；该接口不返回密钥，也不会被加密过滤器处理。
- 默认响应头为 `X-Api-Crypto` 和 `X-Api-Crypto-Key-Id`，查询参数为 `__api_crypto`；信封字段为 `encrypted/version/algorithm/keyId/iv/salt/data/tag`。
- `ReadOnlyBaseController` 只内置分页 `list` 与 `detail` 查询接口，适用于投影和大数据集读取；`BaseController` 在此基础上增加写入和导入接口。所有 JSON 查询与 CRUD 接口默认标注 `@ApiCrypto`；`/import` 文件上传保持明文。业务侧导出、下载、SSE 等流式接口应由领域 Controller 按其筛选条件、权限和数据上限单独定义，且不要标注加密；若 Controller 类级别使用了 `@ApiCrypto`，这些方法应显式覆盖为 `@ApiCrypto(request = false, response = false)`。

```java
@ApiCrypto
@PostMapping("/orders")
public Mono<ApiResponse<OrderVO>> create(@RequestBody OrderCreateRequest request) {
    // 入参已解密为普通 JSON，请按正常 Controller 编写业务逻辑。
}

@ApiCrypto(request = false, response = true)
@GetMapping("/orders/{id}")
public Mono<ApiResponse<OrderVO>> detail(@PathVariable String id) {
    // GET 明文入参，JSON 出参加密。
}
```

## JPA Plus 仓库工厂

引入 `jpa-plus-starter` 后，JPA Plus 自动替换 Spring Data JPA 的默认 Repository 工厂。
业务应用只需按 Spring Boot 的常规规则声明或扫描 Repository，标准 `save`、`saveAll`、
`saveAndFlush` 与删除入口都会使用 JPA Plus 的字段、租户、审计和逻辑删除生命周期；
无需也不应再显式指定 `repositoryFactoryBeanClass`。

带 `@LogicDelete` 的实体调用标准删除方法时会更新删除标记，未标注该注解的实体仍按
Spring Data JPA 语义物理删除。为防止批量 SQL 绕过逻辑删除，带该注解的实体不支持
`DeleteSpecification` 与 `DeleteWrapper` 批量删除；请先查询实体后调用 `delete` 或 `deleteAll`。

## JPA Plus 多租户集成

JPA Plus 的多租户隔离覆盖 QueryWrapper 查询和 Hibernate 普通查询。`web-plus-web` 自动注册 `TenantIdProvider`，从 web-plus 当前用户 SPI 读取 `tenantId`。

继承 `TenantEntity` 的实体保存时，`tenantId` 会由 jpa-plus 在保存前按当前租户自动补齐。需要自定义字段名或占位值时，使用 jpa-plus 原生配置：

```yaml
jpa-plus:
  tenant:
    property: tenantId
    column: tenant_id
    placeholder-values: 0
```

跨租户读取、超级管理员放行等策略不在 Web Plus 中硬编码；业务侧应通过自定义 `TenantIdProvider`、jpa-plus 租户相关 Bean 或关闭特定租户能力显式表达。

## 发布

发布元数据统一从 `gradle.properties` 中的 `pom.*` 读取，模块版本统一从 monorepo 根目录 `gradle/module-versions.properties` 读取。能力族内部模块使用 Gradle `project(...)` 依赖；发布后的消费方只使用 Maven Central 中的 `io.github.guanxiangkai:web-plus-*` Maven 坐标。
