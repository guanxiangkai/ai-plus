<p align="center">
  <h1 align="center">Redis Plus</h1>
  <p align="center">🚀 基于 JDK 25 + Spring Boot 4.1.0 的企业级 Redis 增强框架</p>
  <p align="center">
    <img src="https://img.shields.io/badge/JDK-25-blue" alt="JDK 25"/>
    <img src="https://img.shields.io/badge/Spring%20Boot-4.1.0-green" alt="Spring Boot 4.1.0"/>
    <img src="https://img.shields.io/badge/License-Apache%202.0-orange" alt="License"/>
    <img src="https://img.shields.io/badge/Version-1.0.1-brightgreen" alt="Version"/>
  </p>
</p>

---

## 📖 项目简介

`redis-plus` 是一个面向 Spring 生态的模块化 Redis 增强框架，聚焦以下几类常见企业能力：

- **分布式锁**：基于 Redisson 的统一锁工厂 + 注解式切面接入，支持互斥锁、读写锁、WatchDog 自动续期
- **三级缓存**：L1 本地缓存（Caffeine）+ L2 Redis 缓存 + L3 回源保护，内置防穿透/防击穿
- **缓存增强**：布隆过滤器（`@BloomCheck`）、批量读写、防穿透/防雪崩策略 SPI
- **业务保障**：限流（`@RateLimit`）、幂等（`@Idempotent`）、Redis List/Stream 消息队列
- **治理运维**：Micrometer 指标、Actuator 健康检查、锁/缓存全链路可观测性
- **多 Redis 数据源**：`@RedisDS` 注解路由 + 编程式 `RedisDataSourceContext` 切换

设计目标：

- **模块化按需引入**：每个能力单独成模块，可拆可合
- **Starter 优先**：Spring Boot 项目可直接使用聚合 `redis-plus-starter`，也可只引入单能力 `redis-plus-*-starter`
- **性能优先依赖策略**：L1 使用 Caffeine；不引入 Micrometer/Actuator 时自动跳过指标与健康检查
- **SPI 可扩展**：所有核心能力均提供 SPI 扩展点，用户可替换默认实现

---

## 🏗️ 模块结构

| 模块                      | 职责                                                                                       |
|-------------------------|------------------------------------------------------------------------------------------|
| `redis-plus-core`       | 核心基础设施：统一异常、Key 规范、序列化抽象、TTL 策略、事件模型（`ApplicationEvent`）、指标 SPI                          |
| `redis-plus-lock`       | Redisson 分布式锁、读写锁、WatchDog、锁降级、锁事件、`LockKeyResolver`/`LockFailureHandler`/`LockEventListener` SPI |
| `redis-plus-datasource` | 多 Redis 数据源切换、路由连接工厂（实现 `RedisConnectionFactory`）、读写路由、租户命名空间扩展                          |
| `redis-plus-cache`      | L1 Caffeine + L2 Redis + L3 回源保护的三级缓存，内置防穿透/防击穿；通过 `CacheLoadProtection` SPI 解耦锁依赖       |
| `redis-plus-enhance`    | 布隆过滤器（`@BloomCheck`）、批量缓存操作、防穿透/防击穿/防雪崩策略 SPI                                            |
| `redis-plus-ratelimit`  | 限流（`@RateLimit`，固定窗口/滑动窗口/Redisson 分布式令牌桶/漏桶）、限流算法 SPI                                 |
| `redis-plus-idempotent` | 幂等（`@Idempotent`）、幂等执行器与状态存储 SPI                                                         |
| `redis-plus-queue`      | Redis List/Stream 队列、受控订阅运行时、非阻塞重试、同步拉取 delivery/ACK 语义                                  |
| `redis-plus-governance` | Micrometer 指标（锁/缓存/限流/幂等全覆盖）、Actuator 健康检查、高可用治理扩展                                       |
| `redis-plus-*-starter`  | 单能力 Spring Boot 自动装配入口，例如 `redis-plus-lock-starter`、`redis-plus-cache-starter`、`redis-plus-queue-starter` |
| `redis-plus-starter`    | Spring Boot 聚合入口，传递全部单能力 starter                                                           |

---

## ✨ 支持的注解

| 注解                      | 模块           | 说明                 |
|-------------------------|--------------|--------------------|
| `@RedisLock`            | `lock`       | 互斥分布式锁             |
| `@RedisReadLock`        | `lock`       | 分布式读锁（读写锁中的读锁）     |
| `@RedisWriteLock`       | `lock`       | 分布式写锁（读写锁中的写锁）     |
| `@RedisDS`              | `datasource` | 方法/类级多数据源路由        |
| `@ThreeLevelCacheable`  | `cache`      | 三级缓存查询             |
| `@ThreeLevelCacheEvict` | `cache`      | 三级缓存失效             |
| `@BloomCheck`           | `enhance`    | 布隆过滤器拦截非法 Key      |
| `@RateLimit`            | `ratelimit`  | 限流（支持多算法）          |
| `@Idempotent`           | `idempotent` | 幂等控制（基于 Redis 状态机） |

---

## ✨ Starter 自动装配内容

当前各 `redis-plus-*-starter` 分别注册以下自动装配；`redis-plus-starter` 仅聚合这些单能力 starter：

| 自动装配                                   | 触发条件                                                        | 说明                                                                                                        |
|----------------------------------------|-------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `RedisPlusLockAutoConfiguration`       | classpath 中存在 `RedissonClient`                                | 注册/复用 `RedissonClient`、`RedissonLockBackend`、`RedisLockFactory`、`LockAspect`、`SpelLockKeyResolver`（默认） |
| `RedisPlusCacheAutoConfiguration`      | classpath 中存在 `StringRedisTemplate`                         | 注册本地缓存提供者、缓存模板、TTL 策略、缓存 AOP 切面                                                                           |
| `RedisPlusDataSourceAutoConfiguration` | 默认总会装配                                                      | 单数据源模式下兜底包装 Spring 连接工厂；配置 `redis-plus.datasource.sources.*` 时自动切换多数据源，激活 `@RedisDS` 路由切面                 |
| `RedisPlusEnhanceAutoConfiguration`    | 默认装配，布隆切面受 `redis-plus.enhance.bloom.enabled`（默认 `true`）控制  | 注册 `RedisBitmapBloomFilter`（默认布隆过滤器）、`BloomCheckAspect`、`RedisBatchCacheOperations`                       |
| `RedisPlusRateLimitAutoConfiguration`  | classpath 中存在 `StringRedisTemplate`                         | 注册固定窗口/滑动窗口/Redisson 分布式令牌桶/漏桶四种限流器与 `RateLimitAspect`；没有 `RedissonClient` 时回退到本地 Bucket4j            |
| `RedisPlusIdempotentAutoConfiguration` | classpath 中存在 `StringRedisTemplate`                         | 注册 `RedisIdempotentExecutor`、`IdempotentAspect`                                                           |
| `RedisPlusQueueAutoConfiguration`      | classpath 中存在 `StringRedisTemplate`                         | 注册 `RedisQueueFactory` 与受控异步执行器，创建带非阻塞重试 / 死信 / 批量轮询配置的队列实例                                               |
| `RedisPlusGovernanceAutoConfiguration` | classpath 中存在 `io.micrometer.core.instrument.MeterRegistry` | 注册 `MicrometerRedisPlusMetrics`（替换 Noop 指标）；classpath 中存在 Actuator 健康类时额外注册 `RedisPlusHealthContributor`  |

---

## 📦 获取依赖

### 环境要求

| 项目          | 版本                |
|-------------|-------------------|
| JDK         | `25`              |
| Spring Boot | `4.1.0+`          |
| Gradle      | `9.6.1`（Kotlin DSL） |

> 构建使用 JDK 25 toolchain，不需要 `--enable-preview`。

### Gradle（推荐）

```kotlin
dependencies {
    // 推荐：Spring Boot 项目直接接入 starter
    implementation("io.github.guanxiangkai:redis-plus-starter:1.0.1")

    // 可选：启用 Micrometer 指标 / 健康检查
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // 可选：Prometheus 指标格式
    implementation("io.micrometer:micrometer-registry-prometheus")
}
```

### 按模块引入

```kotlin
dependencies {
    // 推荐：按能力引入 starter，可获得对应自动装配
    implementation("io.github.guanxiangkai:redis-plus-lock-starter:1.0.1")
    implementation("io.github.guanxiangkai:redis-plus-cache-starter:1.0.1")
    implementation("io.github.guanxiangkai:redis-plus-queue-starter:1.0.2")

    // 或者只引入纯功能模块，自行装配 Bean
    implementation("io.github.guanxiangkai:redis-plus-core:1.0.1")
    implementation("io.github.guanxiangkai:redis-plus-lock:1.0.1")
    implementation("io.github.guanxiangkai:redis-plus-cache:1.0.1")
    implementation("io.github.guanxiangkai:redis-plus-enhance:1.0.1")
    implementation("io.github.guanxiangkai:redis-plus-ratelimit:1.0.1")
    implementation("io.github.guanxiangkai:redis-plus-idempotent:1.0.1")
    implementation("io.github.guanxiangkai:redis-plus-queue:1.0.2")
    implementation("io.github.guanxiangkai:redis-plus-governance:1.0.1")
    implementation("io.github.guanxiangkai:redis-plus-datasource:1.0.1")
}
```

### Maven

```xml

<dependency>
    <groupId>io.github.guanxiangkai</groupId>
    <artifactId>redis-plus-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

---

## ⚙️ 配置说明

各能力 starter 当前真实绑定的配置前缀是：

```yaml
redis-plus:
  datasource:    # 多数据源（可选）
  lock:          # 分布式锁
  cache:         # 三级缓存
  enhance:       # 缓存增强（布隆 + 批量）
  ratelimit:     # 限流模块
  idempotent:    # 幂等模块
  queue:         # 队列模块
```

完整参考见：[`配置事例.yml`](配置事例.yml)

一个最小可用示例：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      timeout: 3s

redis-plus:
  lock:
    enabled: true
    key-prefix: "demo:lock:"
    default-lease: 30s
    default-wait: 5s
    redisson:
      address: "redis://localhost:6379"
      database: 0
      connect-timeout: 10s
  cache:
    enabled: true
    key-prefix: "demo:cache:"
    default-ttl: 30m
    jitter-ratio: 0.1
    local:
      maximum-size: 10000
      ttl: 5m
    runtime:
      load-lock-wait: 10s
      load-lock-lease: 30s
      clear-scan-batch-size: 1000
      clear-delete-batch-size: 1000
      null-value-ttl-ratio: 0.2
      null-value-ttl-minimum: 30s
      batch-local-cache-ttl: 5m
      batch-null-value-cache-ttl: 5m
  enhance:
    enabled: true
    bloom:
      enabled: true
      expected-insertions: 1000000
      false-positive-probability: 0.01
  ratelimit:
    enabled: true
    key-prefix: "demo:ratelimit:"
    token-bucket-refill-rate: 100
  idempotent:
    enabled: true
    key-prefix: "demo:idempotent:"
  queue:
    enabled: true
    key-prefix: "demo:queue:"
    default-consumer-group: my-consumers
    max-retry-attempts: 3
    batch-size: 10
    poll-timeout: 2s
    reclaim-on-start: true
    pending-reclaim-idle-time: 5m
    max-stream-length: 0
    read-failure:
      initial-backoff: 250ms
      max-backoff: 30s
      jitter-factor: 0.2
```

说明：

- **L1 本地缓存**：使用 Caffeine 并始终设置 TTL
- **缓存运行策略**：`redis-plus.cache.runtime` 统一配置回源锁、清理批次、空值 TTL 以及批量缓存回填 TTL；程序化装配使用 `CacheRuntimePolicy.defaults()`。
- **模块可显式关闭**：`redis-plus.lock/cache/enhance/ratelimit/idempotent/queue/datasource.enabled=false`
  会关闭对应能力的自动装配，适合只按需启用部分 starter 能力
- **`@ThreeLevelCacheable.localTtl`**：L1 TTL 取 `localTtl` 与 L2 剩余 TTL 的较小值；未配置时与 L2 保持一致
- **未引入 Actuator / Micrometer**：治理模块相关 Bean 不会强行装配
- **`@RateLimit` 参数语义**：
  - `algorithm` 是字符串扩展点，默认 `SLIDING_WINDOW`；自定义算法只需注册 `RateLimitAlgorithm`
  - 固定窗口 / 滑动窗口：使用 `limit + window + unit`
  - 令牌桶：默认优先由 Redisson 提供分布式后端，推荐显式使用 `capacity + refillTokens + refillPeriod + refillUnit`
  - 漏桶：推荐显式使用 `capacity + leakTokens + leakPeriod + leakUnit`
- **令牌桶默认值**：未指定 `capacity` 时使用 `limit`；未指定 `refillTokens` 时使用
  `redis-plus.ratelimit.token-bucket-refill-rate`
- **锁后端**：`RedisLockFactory` 负责编排，默认后端为 `RedissonLockBackend`；可通过 `LockBackend` 扩展。
  `@RedisLock`、`@RedisReadLock`、`@RedisWriteLock` 未指定 `waitTime` 时使用 `redis-plus.lock.default-wait`。
- **核心访问契约**：常规 Redis 命令使用 `RedisBackend`，Lua 使用 `RedisScriptExecutor`，Key 使用 `KeyNamingStrategy`。
- **编译校验**：Java 编译任务启用 Error Prone、`-Werror` 与 NullAway `ERROR`。
- **默认序列化**：`JacksonValueSerializer` 使用显式 `{ "@type", "payload" }` envelope；目标类型为 `Object` 时校验 envelope 类型白名单。
- **Spring Cache**：缓存 starter 注册 `RedisPlusCacheManager`，标准 `@Cacheable` 可复用三级缓存能力。
- **幂等状态**：`IdempotentState` record 存储 `PROCESSING`、`DONE`、`FAILED` 和结果元数据。
- **队列默认工厂**：`redis-plus.queue.key-prefix` 派生 List/Stream 前缀，
  `redis-plus.queue.default-consumer-group` 会成为 `RedisQueueFactory#createStreamQueue(queueName, type)` 的默认消费组；
  `max-retry-attempts`、`batch-size`、`poll-timeout` 会直接作用于消费运行时；
  Redis 读取异常按 `read-failure` 指数退避，Stream 毒消息只有在 `PoisonMessageHandler` 隔离成功后才会 ACK
- **队列生命周期**：默认构造器创建并管理异步执行器；`stop()` 会同步关闭它。自定义 `asyncExecutor` 的生命周期由调用方管理。
- **队列 API**：List 工厂返回 `SimpleQueue`，Stream 工厂返回 `AckQueue`；
  只有 `AckQueue` 暴露 `reclaimPending(...)`，避免 List 队列继承无意义的 ACK/Pending 语义
- **订阅模型**：`MessageQueue#subscribe(...)` 返回 `QueueSubscription`，由统一异步执行器托管，停止语义可控。
- **同步拉取语义**：`MessageQueue#receive(...)` 返回 `QueueDelivery`；
  可通过 `QueueDelivery#mode()` 判断交付模式：
  `ALREADY_DEQUEUED`（List，消息已出队，ack 为 no-op）和
  `PENDING_ACKNOWLEDGMENT`（Stream，需显式 `acknowledge()` 才会真正 XACK）
- **重试执行**：默认队列运行时通过统一异步执行器调度重试，不阻塞消费线程。
- **幂等 FAILED 重试**：并发请求通过 Redis CAS 竞争状态切换；只有获胜请求进入 `PROCESSING` 并执行业务，其余请求获得处理中结果。
- **运行时观测**：lock、ratelimit、idempotent 等路径使用 `RedisPlusObserver`；cache、governance 通过 `RedisPlusMetrics` 暴露指标。
- **多 Redis 数据源**：
    - `redis-plus.datasource.sources` 下的每个 **map key 就是路由标签**，与 `@RedisDS` 注解值一一对应
    - `database` 字段是 Redis DB 编号（0~15），不是路由标签
    - `primary` 指定无注解时的默认数据源（必须在 `sources` 中存在）
    - 配置 `sources` 时 `MultiRedisConnectionFactory` 是主连接工厂，`RedisTemplate` 自动使用路由能力
    - 单数据源时只创建后备包装工厂，普通注入仍使用 Spring Boot 或应用定义的默认连接
    - `MultiRedisConnectionFactory` 实现了 `RedisConnectionFactory`，可直接注入到 `RedisTemplate`
    - 也支持自行注册 `MultiRedisConnectionFactory` Bean（编程式高级用法）
- **事件监听**：
  - `RedisPlusEvent` 继承 Spring `ApplicationEvent`，支持类型化 `@EventListener` 监听
  - 示例：`@EventListener public void on(LockAcquiredEvent e) { ... }`

---

## 🔌 SPI 扩展点

所有 SPI 接口均通过 Spring `@ConditionalOnMissingBean` 注册默认实现，用户只需注册同类型 Bean 即可覆盖。

| SPI 接口                        | 模块           | 说明                    | 默认实现                                                             |
|-------------------------------|--------------|-----------------------|------------------------------------------------------------------|
| `LockKeyResolver`             | `lock`       | 锁 Key SpEL 解析         | `SpelLockKeyResolver`                                            |
| `LockFailureHandler`          | `lock`       | 锁获取失败处理（抛异常/降级）       | 抛 `RedisLockException`                                           |
| `LockEventListener`           | `lock`       | 锁生命周期事件回调             | 无（注册即生效）                                                         |
| `LocalCacheProvider`          | `cache`      | L1 本地缓存容器             | `CaffeineLocalCacheProvider`                                    |
| `CacheKeyResolver`            | `cache`      | 缓存 Key 解析             | SpEL 解析                                                          |
| `CacheConsistencyStrategy`    | `cache`      | 写后缓存一致性策略             | 直接失效                                                             |
| `CacheLoadProtection`         | `cache`      | 缓存回源保护（防击穿互斥锁）        | 有 lock 模块时用分布式锁，否则 JVM 本地锁                                       |
| `ValueSerializer`             | `core`       | 统一值序列化                | `JacksonValueSerializer`                                         |
| `BloomHashProvider`           | `enhance`    | 布隆过滤器哈希函数             | FNV-1a 双哈希                                                       |
| `BloomStorageStrategy`        | `enhance`    | 布隆过滤器存储策略             | Redis Bitmap                                                      |
| `RateLimitAlgorithm`          | `ratelimit`  | 限流算法扩展                | 固定窗口 / 滑动窗口 / 令牌桶 / 漏桶                                     |
| `IdempotentStateStore`        | `idempotent` | 幂等状态存储                | Redis 状态机                                                        |
| `QueueRetryStrategy`          | `queue`      | 队列消费重试策略              | 固定间隔重试                                                          |
| `DeadLetterHandler`           | `queue`      | 队列死信处理                | 记录日志并丢弃                                                         |
| `PoisonMessageHandler`        | `queue`      | 原始毒消息隔离               | 不输出载荷的安全日志并丢弃                                                   |
| `MetricsTagContributor`       | `governance` | 自定义 Micrometer 指标 tag | 无（注册即生效）                                                         |

---

## 🧩 仓库内部依赖约定（面向贡献者）

本仓库使用 version catalog bundles 组织 Gradle 依赖，核心约定如下：

- `*-public-api`：公开签名依赖，通常对应 `api`
- `*-impl-support`：实现支撑依赖，通常对应 `implementation`
- starter 模块只声明自身能力需要的 Spring Boot/Jackson/Validation 依赖；聚合 starter 不持有自动配置类

例如：

- `libs.bundles.core-public-api`
- `libs.bundles.cache-impl-support`
- `libs.bundles.starter.impl.support`

---

## 🚀 本地构建

```bash
./gradlew build
```

如只想验证 Starter：

```bash
./gradlew :redis-plus-starter:build
```

---

## 📋 版本信息

| 属性       | 值              |
|----------|----------------|
| Group    | `io.github.guanxiangkai` |
| Version  | `1.0.1`    |
| JDK      | `25`           |
| Encoding | `UTF-8`        |

---

## 📄 开源协议

本项目基于 [Apache License 2.0](LICENSE) 开源协议发布。


---

<p align="center">
  Made with ❤️ by <a href="https://github.com/guanxiangkai">guanxiangkai</a>
</p>
