# JPA Plus

> 🚀 基于 JDK 25 + Spring Boot 4.1.1 的企业级 JPA 增强框架

![JDK 25](https://img.shields.io/badge/JDK-25-blue)
![Spring Boot 4.1.1](https://img.shields.io/badge/Spring%20Boot-4.1.1-green)
![License Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-orange)

---

## 📖 项目简介

**JPA Plus** 是一个轻量级、模块化的 JPA 增强框架，致力于在**不改变 JPA 使用习惯**
的前提下，为企业级应用提供查询增强、字段治理、数据拦截、审计、多数据源、分库分表等能力。

### 核心理念

- **只增强，不改变** —— 兼容 `JpaRepository` 使用方式
- **模块化按需引入** —— 不引入、不配置就不生效
- **注解驱动 + SPI 扩展** —— 开箱即用，也支持业务侧扩展
- **现代 Java 技术栈** —— 使用 `ScopedValue`、虚拟线程、`record`、`sealed interface` 等能力

---

## ✨ 当前已支持

### 核心能力

| 能力                | 说明                                                                             | 注解 / API                                                  |
|-------------------|--------------------------------------------------------------------------------|-----------------------------------------------------------|
| 查询增强              | Lambda DSL、多表 Join、分页优化、多方言 SQL 编译                                             | `QueryWrapper` / `JoinWrapper`                            |
| GROUP BY / HAVING | 聚合分组查询，HAVING 支持聚合函数条件                                                         | `groupBy()` / `having(AggregateCondition)`                |
| Keyset 深分页        | 基于游标的深分页优化，性能与页码无关                                                             | `QueryExecutor.pageKeyset()` / `KeysetCursor`             |
| ID 生成             | 雪花算法 / UUID / 自定义策略                                                            | `@AutoId`                                                 |
| 自动填充              | 创建/更新人、创建/更新时间自动填充                                                             | `@CreateTime` / `@UpdateTime` / `@CreateBy` / `@UpdateBy` |
| 字段加密              | 保存前加密、查询后解密，由密钥提供者按密钥标识解析                                                  | `@Encrypt` / `EncryptKeyProvider`                         |
| 数据脱敏              | 查询结果自动脱敏                                                                       | `@Desensitize`                                            |
| 敏感词检测             | 内置 DFA，可选 houbb 高级引擎                                                           | `@SensitiveWord`                                          |
| 字典回写              | 查询后自动翻译 label（批量翻译 + TTL 缓存）                                                   | `@Dict` / `DictProvider#getLabels`                        |
| 乐观锁               | 保存时版本号维护                                                                       | `@Version`                                                |
| 逻辑删除              | 查询自动过滤，删除改写为标记删除                                                               | `@LogicDelete`                                            |
| 自动排序              | 未显式排序时注入默认排序                                                                   | `@AutoOrderBy`                                            |
| 数据权限              | 查询前注入权限条件，支持方法级 `@DataScope`                                                   | `PermissionInterceptor` / `@DataScope`                    |
| 多租户               | 查询前注入租户条件，租户列名可配置                                                              | `TenantInterceptor`                                       |
| 审计事件              | 保存/删除后发布审计事件；SAVE 快照会按实体标识读取持久化前镜像，DELETE 同样保留删除前快照（`FieldDiff(before, null)`） | `AuditEvent` / `SnapshotAuditInterceptor`                 |
| 多数据源              | `@DS` + `ScopedValue` 路由（支持 SpEL 表达式）                                          | `@DS` / `JpaPlusContext`                                  |
| 分库分表              | 分片规则、单分片路由、`@ShardingQuery`、Hash/Range/Date 算法                                 | `@Sharding` / `@ShardingQuery` / `ShardingRouter`         |
| 跨分片查询             | 顺序/并行散射查询 SPI，内置顺序和虚拟线程并行实现                                                    | `CrossShardQueryExecutor`                                 |

### 已落地的增强能力

- **更多数据库方言**：MySQL、MariaDB、PostgreSQL、Oracle、SQL Server、SQLite、H2、ClickHouse、达梦、Kingbase
- **Micrometer 指标**：拦截器链耗时、字段处理耗时
- **拦截器链动态扩展**：优先 Spring Bean，支持 JDK 标准 `ServiceLoader`
- **审计事件异步模式**：基于虚拟线程，支持自定义 `AuditEventErrorHandler`
- **分片事务边界控制**：`REJECT / BEST_EFFORT / SEATA`
- **GROUP BY / HAVING 聚合查询**：`groupBy()` + `having(AggregateCondition)`
- **Keyset 深分页**：`pageKeyset()` + `KeysetCursor`，性能与页码无关
- **方法级 `@DataScope`**：Repository 方法注解优先于实体类注解
- **租户列名可配置**：`TenantIdProvider` SPI + `jpa-plus.tenant.column`（注册 Bean 即自动生效）
- **`@ShardingQuery` 分片读路由**：注解声明分片键表达式，自动路由查询
- **内置分片算法扩展**：`HashMod`（默认）、`Range`（范围分片）、`Date`（日期分片）
- **跨分片散射查询 SPI**：`CrossShardQueryExecutor`（顺序 + 虚拟线程并行两种实现）
- **字典 TTL 缓存**：`CachedDictProvider` 自动装饰 `DictProvider`，默认 300 秒
- **字段引擎批处理**：字典翻译从 N 次调用收敛为 1 次批量翻译
- **批量操作 API**：`updateBatch / deleteBatch / upsertBatch` 统一入口
- **查询流式处理**：`QueryExecutor.stream()` / `JpaPlusRepository.stream()`
- **Starter 自动装配拆分**：`Core / Query / Field / Interceptor / Audit` 五个聚焦配置类（全部统一收敛在 starter）
- **审计轻量快照**：`SnapshotAuditInterceptor` + `AuditSnapshot` + `FieldDiff`（SAVE 通过隔离 `EntityManager`
  对比持久化前状态，DELETE 同样完整记录）
- **Seata 分片事务接入模板**：`SeataShardingTransactionTemplate`
- **SQL 编译热路径优化**：`AbstractSqlCompiler` 全面替换 `Collectors.joining()` 为 `StringJoiner`，消除中间流分配
- **`PermissionInterceptor` 缓存增强**：缺席注解同样缓存，彻底消除重复注解扫描
- **`SnapshotAuditInterceptor` 字段反射缓存**：每个 Entity 类的 `setAccessible()` 仅调用一次
- **`MicrometerJpaPlusMetrics` Timer 缓存**：按 tag 缓存 `Timer` 实例，消除每次操作的 Registry 查找开销
- **未知方言 `warn` 日志**：`SqlCompilerRegistry` 回退时自动打印可诊断的 `WARN` 日志

---

## 🏗️ 模块说明

```text
jpa-plus-core         核心引擎：拦截器链、字段引擎、SPI（Ordered 排序契约）、执行器
jpa-plus-query        查询增强：DSL、AST、SQL 编译、多方言分页
jpa-plus-field        字段治理：ID、自动填充、加密、脱敏、字典、敏感词、乐观锁
jpa-plus-interceptor  数据拦截：逻辑删除、自动排序、数据权限、多租户（TenantIdProvider SPI）
jpa-plus-audit        审计：AuditEvent、数据变更快照、事件发布（自动装配统一收敛在 starter）
jpa-plus-datasource   多数据源：@DS、动态路由、刷新、datasource-proxy / Druid 接入
jpa-plus-sharding     分库分表：分片规则、分片算法、单分片路由、跨分片策略
jpa-plus-starter      Spring Boot 自动装配入口（含审计、拦截器、字段、查询、核心全部自动配置）
```

---

## 📦 获取依赖

### 环境要求

| 要求          | 最低版本                        |
|-------------|-----------------------------|
| JDK         | 25+                         |
| Spring Boot | 4.1.1+                      |
| Gradle      | 9.5+                        |

### 推荐方式：Starter

```kotlin
// build.gradle.kts
dependencies {
  implementation("io.github.guanxiangkai:jpa-plus-starter:2.0.1")
}
```

### 按需引入

```kotlin
// build.gradle.kts
dependencies {
  implementation("io.github.guanxiangkai:jpa-plus-core:1.0.1")
  implementation("io.github.guanxiangkai:jpa-plus-query:1.0.1")
  implementation("io.github.guanxiangkai:jpa-plus-field:1.0.1")
  implementation("io.github.guanxiangkai:jpa-plus-interceptor:1.0.1")
  implementation("io.github.guanxiangkai:jpa-plus-audit:1.0.2")
  implementation("io.github.guanxiangkai:jpa-plus-datasource:1.0.1")
  implementation("io.github.guanxiangkai:jpa-plus-sharding:1.0.1")
}
```

### 依赖边界说明

| 入口 / 模块                | 适用场景                      | 会传递暴露的能力                                                             | 不会替你强绑的内容                                                    |
|------------------------|---------------------------|----------------------------------------------------------------------|--------------------------------------------------------------|
| `jpa-plus-starter`     | Spring Boot 项目开箱即用        | 各功能模块 API、`JpaRepository`/`Pageable`/`EntityManager`、Spring 事件相关 ABI | Micrometer、`datasource-proxy`、Druid 等可选增强仍需业务方自行放入 classpath |
| `jpa-plus-query`       | 仅使用查询 DSL / Join / 分页     | `EntityManager`、Spring Data 分页抽象                                     | Hibernate Flush 脏检查实现细节                                      |
| `jpa-plus-field`       | 仅使用字段治理                   | 核心字段治理契约                                                             | `houbb-sensitive-word`、Hutool 等第三方增强                         |
| `jpa-plus-interceptor` | 仅使用逻辑删除 / 权限 / 多租户 / 自动排序 | core 拦截器契约 + query AST / `Condition` SPI                             | 无额外 Spring 基础设施                                              |
| `jpa-plus-audit`       | 仅使用数据层审计事件                | core 操作类型 / 拦截器契约 + Spring 事件 / JPA 快照 API                           | Boot 自动装配细节                                                  |
| `jpa-plus-datasource`  | 仅使用 `@DS` / 动态数据源         | 数据源事件、JDBC 路由、健康检查 API                                               | AOP 运行时细节                                                    |
| `jpa-plus-sharding`    | 仅使用分库分表                   | core 拦截器契约 + datasource 上下文契约                                        | SpEL / AOP / Boot 自动装配细节                                     |

> 设计原则：**进入公开构造器 / 方法签名 / 继承树的类型保留在 `api`；只在内部实现使用的依赖下沉到 `implementation`
；真正可选增强保持 `compileOnly`。**

> 类路径自动装配由 `jpa-plus-starter` 托管。直接引入 `jpa-plus-sharding` 只获得分片 API / SPI；
> 不使用 starter 时，如需启用 Boot 自动装配，请显式 `@Import(ShardingAutoConfiguration.class)`。

### 可选增强依赖

以下能力默认不会被 `starter` 强制传递，按需引入即可自动激活：

```kotlin
dependencies {
    // 可观测性
    implementation("io.micrometer:micrometer-core")

    // JDBC SQL 追踪
    runtimeOnly("net.ttddyy:datasource-proxy:1.11.0")

    // 可选连接池
    runtimeOnly("com.alibaba:druid:1.2.28")

    // 字段治理第三方增强
    implementation("com.github.houbb:sensitive-word:0.29.5")
    implementation("cn.hutool:hutool-crypto:5.8.46")
    implementation("cn.hutool:hutool-core:5.8.46")
}
```

---

## ⚙️ 最小配置

### 1）单库项目

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/demo
    username: root
    password: ${DB_PASSWORD}

jpa-plus:
  debug:
    enabled: false
```

### 2）多数据源项目

```yaml
spring:
  datasource:
    dynamic:
      primary: master
      strict: true
      datasource:
        master:
          url: jdbc:mysql://localhost:3306/master_db
          username: root
          password: ${MASTER_DB_PASSWORD}
        slave_1:
          url: jdbc:mysql://localhost:3306/slave_db
          username: root
          password: ${REPLICA_DB_PASSWORD}
```

### 3）加密密钥配置

```yaml
jpa-plus:
  encrypt:
    enabled: true
    active-version: primary
    keys:
      primary: ${JPA_PLUS_ENCRYPT_KEY:}
```

### 4）分库分表项目

```yaml
jpa-plus:
  sharding:
    enabled: true
    cross-shard-policy: REJECT
    rules:
      - logic-table-name: t_order
        db-count: 2
        table-count: 4
        db-pattern: order_db_{index}
        table-pattern: t_order_{index}
        sharding-key-field: userId
```

> `cross-shard-policy` 默认推荐 `REJECT`。  
> 若需要分布式事务，可切换为 `SEATA`，并由业务方通过 `DataSourcePostProcessor` SPI 自行接入具体实现。

---

## 🚀 快速使用

### Repository 查询增强

```java
public interface UserRepository extends JpaPlusRepository<User, Long> {
}
```

`jpa-plus-starter` 会自动接管 Spring Data JPA 的默认 Repository 工厂，因此普通
`JpaRepository` 也无需显式指定工厂即可使用统一生命周期：`save`、`saveAll`、
`saveAndFlush` 会依次进入 ID、字段填充、租户和审计链。带 `@LogicDelete` 的实体通过
标准 `delete` 系列方法逻辑删除，未标注该注解的实体仍执行 Spring Data JPA 的物理删除。
为防止批量 SQL 绕过逻辑删除，带 `@LogicDelete` 的实体不支持 `DeleteSpecification` 和
`DeleteWrapper` 批量删除；请先查询实体后调用 `delete` 或 `deleteAll`。

```java
var users = userRepository.list(
        QueryWrapper.of(User.class)
                .eq(User::getStatus, 1)
                .like(User::getName, "Tom")
                .orderByDesc(User::getId)
);
```

### 批量操作 API（v3）

```java
// 批量条件更新
int updated = userRepository.updateBatch(List.of(
                UpdateWrapper.from(User.class).eq(User::getStatus, 0).set(User::getStatus, 1),
                UpdateWrapper.from(User.class).eq(User::getStatus, 2).set(User::getStatus, 3)
        ));

// 批量条件删除
int deleted = userRepository.deleteBatch(List.of(
        DeleteWrapper.from(User.class).eq(User::getStatus, -1)
));

// 批量 upsert（新增或更新）
List<User> merged = userRepository.upsertBatch(users);
```

### 流式查询（大结果集恒定内存）

```java

@Transactional(readOnly = true)
public void exportUsers() {
    try (Stream<User> stream = userRepository.stream(
            QueryWrapper.from(User.class).orderByAsc(User::getId)
    )) {
        stream.forEach(this::writeCsv);
    }
}
```

### 字段治理示例

```java

@Entity
public class User {

    @Id
    @AutoId
    private Long id;

    @CreateTime
    private LocalDateTime createdTime;

    @UpdateTime
    private LocalDateTime updatedTime;

    @Encrypt
    private String phone;

    @Desensitize
    private String idCard;

    @Version
    private Long version;
}
```

### 逻辑删除 / 默认排序

```java

@Entity
public class Order {

    @LogicDelete
    private Integer deleted;

    @AutoOrderBy(priority = 1, direction = Direction.DESC)
    private LocalDateTime createdTime;
}
```

### 多数据源切换

```java

@DS("slave_1")
public Page<User> pageFromSlave(Pageable pageable) {
    return userRepository.findAll(pageable);
}
```

```java
// 支持 SpEL 动态路由（方法参数 / Spring Bean 均可参与表达式）
@DS("#{#tenant + '_ds'}")
public Page<User> pageByTenant(String tenant, Pageable pageable) {
    return userRepository.findAll(pageable);
}
```

### GROUP BY / HAVING

```java
// SELECT t.status, COUNT(*) FROM t_order t GROUP BY t.status HAVING COUNT(*) > 10
var results = queryExecutor.list(
        QueryWrapper.from(Order.class)
                .groupBy(Order::getStatus)
                .having(new AggregateCondition(AggregateFunction.COUNT, null, Operator.GT, 10))
);
```

### Keyset 深分页（高性能翻页）

```java
// 首页
KeysetPageResult<Order> page1 = queryExecutor.pageKeyset(
        QueryWrapper.from(Order.class).orderByDesc(Order::getId),
        KeysetCursor.first(20)
);

// 下一页（使用上一页返回的游标，无 OFFSET 全表扫描）
if (page1.hasNext()) {
    KeysetPageResult<Order> page2 = queryExecutor.pageKeyset(wrapper, page1.nextCursor());
}
```

### 方法级 `@DataScope`

```java
public interface OrderRepository extends JpaPlusRepository<Order, Long> {

    // 此方法级注解优先于实体类上的 @DataScope
    @DataScope(type = DataScopeType.SELF, userColumn = "creator_id")
    List<Order> findMyOrders();
}
```

### 多租户配置（TenantIdProvider）

```java
// 注册 TenantIdProvider Bean 即可自动启用多租户拦截
@Bean
public TenantIdProvider tenantIdProvider() {
    return () -> SecurityContextHolder.getContext().getOrgId();
}
```

```yaml
# 可选：自定义租户列名（默认 tenant_id）
jpa-plus:
  tenant:
    column: org_id
```

```java
// 或直接自定义 TenantInterceptor Bean（优先级更高）
@Bean
public TenantInterceptor tenantInterceptor(TenantIdProvider provider) {
    return new TenantInterceptor("org_id", provider);
}
```

### `@AuditExclude` 字段过滤

```java

@Entity
public class User {

    @Encrypt
    @AuditExclude          // 加密字段不计入快照对比
    private String phone;

    @AuditExclude          // 大 TEXT 字段跳过审计
    @Column(columnDefinition = "TEXT")
    private String remark;

    private String name;   // 照常参与审计快照
}
```

### 自定义字典翻译源（`DictProvider`）

```java

@Component
public class DemoDictProvider implements DictProvider {

    @Override
    public List<DictTranslateItem> getItems(String dictCode) {
        if (!"sys_user_sex".equals(dictCode)) {
            return List.of();
        }
        return List.of(
                new DictTranslateItem("sys_user_sex", "1", "男", "primary", 1),
                new DictTranslateItem("sys_user_sex", "2", "女", "success", 2)
        );
    }

    @Override
    public Map<String, String> getLabels(String dictCode, Set<String> values) {
        // 推荐：直接批量查询，避免逐条翻译
        return DictProvider.super.getLabels(dictCode, values);
    }
}
```

### 字典缓存主动失效（Spring 事件）

```java
// 失效指定字典编码的缓存（字典数据变更后调用）
applicationEventPublisher.publishEvent(DictCacheEvictEvent.of(this, "sys_user_sex"));

// 清除全部字典缓存
applicationEventPublisher.publishEvent(DictCacheEvictEvent.evictAll(this));
```

> 多节点场景：在 MQ 消费方收到字典变更消息后，调用 `publishEvent` 通知本地 `CachedDictProvider` 立即失效。

### 跨分片查询：全局排序 + 分页

```java
// 全局排序（所有分片数据汇聚后内存排序）
List<Order> sortedOrders = crossShardQueryExecutor.executeAllSorted(
        orderRule,
        target -> orderRepository.findByDbAndTable(target.db(), target.table()),
        Comparator.comparing(Order::getCreatedTime).reversed()
);

// 全局排序 + 内存分页（适合运营后台低频翻页导出）
CrossShardQueryExecutor.PagedResult<Order> page1 = crossShardQueryExecutor.executePaged(
        orderRule,
        target -> orderRepository.findByDbAndTable(target.db(), target.table()),
        Comparator.comparing(Order::getCreatedTime).reversed(),
        1,   // page（1-based）
        20   // pageSize
);
if (page1.hasNext()) {
    // 继续翻页 ...
}
```

### 审计快照序列化（`SnapshotSerializer`）

```java
// 默认：字段值保持原始 Java 对象
@Bean
public SnapshotAuditInterceptor snapshotAuditInterceptor(
        AuditEventPublisher publisher, EntityManager em) {
    return new SnapshotAuditInterceptor(publisher, em);  // NOOP serializer
}

// 使用 Jackson 将 LocalDateTime/枚举等序列化为 JSON 字符串
@Bean
public SnapshotAuditInterceptor snapshotAuditInterceptor(
        AuditEventPublisher publisher, EntityManager em, ObjectMapper objectMapper) {
    return new SnapshotAuditInterceptor(publisher, em,
            new JacksonSnapshotSerializer(objectMapper));
}
```

### `FieldEngine` 热重载

```java

@Autowired
FieldEngine fieldEngine;

// 运行时追加处理器（自动按 order 重新排序，缓存自动失效）
fieldEngine.

registerHandler(new MyCustomFieldHandler());

// 按类型移除处理器
        fieldEngine.

unregisterHandler(MyCustomFieldHandler .class);

// 仅清除预计算缓存（不修改处理器列表，下次访问时重建）
fieldEngine.

clearHandlerCache();
```

### 跨分片流式查询（`executeAsStream`）

```java
// 惰性流式遍历：每个分片数据在消费时才加载，无需将全量数据同时加载到内存
try(Stream<Order> stream = crossShardQueryExecutor.executeAsStream(
        orderRule,
        target -> orderRepository.findByTable(target.table()))){
        stream.

forEach(order ->exporter.

write(order));   // 边扫描边写文件
        }

// 带全局排序的流（注意：sorted() 内部需收集全量后排序）
        try(
Stream<Order> sorted = crossShardQueryExecutor.executeAsStream(
        orderRule,
        target -> orderRepository.findByTable(target.table()),
        Comparator.comparing(Order::getCreatedTime).reversed())){
        sorted.

limit(100).

forEach(System.out::println);
}
```

### 字典缓存 MQ 广播失效（Kafka / RocketMQ）

```java
// ── Kafka 适配（引入 spring-kafka 后取消注释）──
@Component
@EnableKafka
public class DictEvictConsumer extends KafkaDictCacheEvictAdapter {
    public DictEvictConsumer(ApplicationEventPublisher pub) {
        super(pub);
    }

    @KafkaListener(topics = "${jpa-plus.dict.evict-topic:dict-change-topic}",
            groupId = "${spring.application.name}-dict-evict")
    public void onMessage(String dictCode) {
        handleEvict(dictCode);  // 发布本地 DictCacheEvictEvent，触发 CachedDictProvider 失效
    }
}

// ── RocketMQ 适配（引入 rocketmq-spring-boot-starter 后取消注释）──
@Component
@RocketMQMessageListener(topic = "dict-change-topic", consumerGroup = "my-app-dict-evict")
public class DictEvictConsumer extends RocketMQDictCacheEvictAdapter
        implements RocketMQListener<String> {
    public DictEvictConsumer(ApplicationEventPublisher pub) {
        super(pub);
    }

    @Override
    public void onMessage(String dictCode) {
        handleEvict(dictCode);
    }
}
```

### `@ShardingQuery` 支持 `Optional` / `Page<T>` 返回类型

```java
public interface OrderRepository extends JpaPlusRepository<Order, Long> {

    // Optional<T> 返回：路由后结果为 null 时自动返回 Optional.empty()
    @ShardingQuery(logicTable = "t_order", keyExpression = "#orderId")
    Optional<Order> findByOrderId(@Param("orderId") Long orderId);

    // Page<T> 返回：路由后结果为 null 时自动返回空 Page（保留 Pageable 元信息）
    @ShardingQuery(logicTable = "t_order", keyExpression = "#userId")
    Page<Order> findByUserId(@Param("userId") Long userId, Pageable pageable);
}
```

### 分片查询（`@ShardingQuery`）

```java
public interface OrderRepository extends JpaPlusRepository<Order, Long> {

    @ShardingQuery(logicTable = "t_order", keyExpression = "#userId")
    List<Order> findByUserId(@Param("userId") Long userId);
}
```

### 审计快照（轻量字段变更记录）

```java

@Bean
public SnapshotAuditInterceptor snapshotAuditInterceptor(
        AuditEventPublisher publisher, EntityManager em) {
    return new SnapshotAuditInterceptor(publisher, em);
}

// 监听带快照的审计事件
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onAudit(DataAuditEvent event) {
    if (event.hasSnapshot()) {
        event.snapshot().diffs().forEach((field, diff) ->
                log.info("Field '{}' changed: {} → {}", field, diff.before(), diff.after()));
    }
}
```

> **DELETE 操作同样生成快照**：删除时 `FieldDiff.before()` 为删除前字段值，`FieldDiff.after()` 为 `null`。
> 可利用该快照还原被删记录的完整状态。
>
> **SAVE 快照实现说明**：`SnapshotAuditInterceptor` 会先通过 `PersistenceUnitUtil#getIdentifier()` 获取实体标识，
> 再使用隔离的 `EntityManager` 读取持久化前镜像，因此对常见的托管实体更新、`@EmbeddedId`、
> 以及 property access 主键映射都能保持稳定的 diff 结果。该能力需要像上例一样显式注册 `SnapshotAuditInterceptor` Bean。

### 审计事件异步发布

```yaml
jpa-plus:
  audit:
    async:
      enabled: true
```

如需自定义异步失败处理，注册 `AuditEventErrorHandler` Bean 即可。

### Micrometer 指标

应用已引入 Micrometer / Actuator 时，JPA Plus 会自动注册指标：

- `jpa.plus.chain.execution`
- `jpa.plus.field.before_save`
- `jpa.plus.field.after_query`

可通过以下配置修改前缀：

```yaml
jpa-plus:
  metrics:
    prefix: demo.jpa
```

### 拦截器链扩展（Spring Bean / ServiceLoader）

推荐优先注册 Spring Bean，Starter 会自动收集并按 `Ordered` 顺序合并：

```java
@Configuration
public class JpaPlusExtensionConfig {

    @Bean
    InterceptorChainContributor myInterceptorContributor() {
        return () -> List.of(new MyCustomInterceptor());
    }
}
```

非 Spring 场景可使用 JDK 标准 `ServiceLoader`：

```text
META-INF/services/io.github.guanxiangkai.jpa.plus.core.interceptor.InterceptorChainContributor
```

贡献者实现示例：

```java
public class MyInterceptorContributor implements InterceptorChainContributor {
    @Override
    public List<DataInterceptor> contribute() {
        return List.of(new MyCustomInterceptor());
    }
}
```

文件内容：

```text
com.example.MyInterceptorContributor
```

---

## 🔌 常见可选增强

### 敏感词高级引擎

```kotlin
dependencies {
    implementation("com.github.houbb:sensitive-word:0.29.5")
}
```

### SQL 追踪

```yaml
spring:
  datasource:
    dynamic:
      datasource-proxy:
        enabled: true
        log-level: debug
        slow-query-threshold: 1000
```

### Druid 连接池

```yaml
spring:
  datasource:
    dynamic:
      pool-type: druid
```

### 分布式事务 SPI 扩展

`jpa-plus-sharding` 不内置具体分布式事务实现。  
若业务需要跨分片事务，推荐做法：

1. 配置 `jpa-plus.sharding.cross-shard-policy=SEATA`
2. 通过 `DataSourcePostProcessor` SPI 包装需要参与分布式事务的数据源
3. 由业务方决定使用 Seata 或其他代理实现方式

---

## 🗺️ 路线图

### 已完成

- [x] 支持更多数据库方言（Oracle、SQL Server、SQLite 等）
- [x] 集成 Micrometer 可观测性指标（拦截器耗时、字段处理耗时等）
- [x] 拦截器链支持 Spring Bean + JDK ServiceLoader 动态扩展（`InterceptorChainContributor`）
- [x] 事件总线异步模式（虚拟线程 + 可配置 `AuditEventErrorHandler`）
- [x] 支持 `GROUP BY` / `HAVING`（`groupBy()` + `having(AggregateCondition)`）
- [x] 支持 Keyset Pagination 深分页优化（`QueryExecutor.pageKeyset()` / `KeysetCursor`）
- [x] 支持方法级 `@DataScope` 解析（Repository 方法注解优先于实体类注解）
- [x] 支持租户列名可配置（`TenantInterceptor` 构造器 / `jpa-plus.tenant.column`）
- [x] 完善分片查询场景与使用示例（`@ShardingQuery`、`RangeShardingAlgorithm`、`DateShardingAlgorithm`）
- [x] 持续优化 Starter 自动装配拆分（`JpaPlusCoreAutoConfiguration` 等四个聚焦配置类）
- [x] 增强字典缓存（`CachedDictProvider` TTL 缓存 + Lambda 解析使用 `MethodHandles` 替代 `setAccessible`）
- [x] 增加审计轻量快照（`SnapshotAuditInterceptor` + `AuditSnapshot` + `FieldDiff`）
- [x] 完善跨分片策略 SPI（`CrossShardQueryExecutor`：顺序 + 虚拟线程并行两种实现）
- [x] 跨分片 Seata 事务接入模板（`SeataShardingTransactionTemplate`）
- [x] **完善 `@ShardingQuery` SpEL 参数绑定**（`ShardingQueryAspect` AOP 拦截 Repository 方法，支持 `@Param` 注解与反射参数名）
- [x] **`SnapshotAuditInterceptor` 增加 `@AuditExclude` 字段过滤注解**（加密/脱敏/大字段可跳过快照采集）
- [x] **`FieldEngine` 增加字段到处理器的映射缓存**（首次计算后缓存 `(handler, field)` 对，后续零 `supports()` 调用）
- [x] **`CrossShardQueryExecutor` 支持全局排序和分页**（`executeAllSorted()` + `executePaged()` + `PagedResult`）
- [x] **`CachedDictProvider` 支持主动失效通知（Spring 事件）**（`DictCacheEvictEvent`，无 Redis 依赖，纯 SPI 扩展）
- [x] **分片模块 H2 集成测试**（`ShardingRoutingTest`：路由一致性、范围分片、跨分片分页聚合全覆盖）
- [x] **SQL 编译热路径 `StringJoiner` 替换**（`AbstractSqlCompiler` 全面去除 `Collectors.joining()`，消除中间流分配开销）
- [x] **`PermissionInterceptor` absent 注解缓存**（缺席注解同样写入缓存，彻底消除重复反射扫描）
- [x] **`SnapshotAuditInterceptor` DELETE 快照修复**（删除操作现在完整保留删除前字段值，以 `FieldDiff(before, null)` 形式输出）
- [x] **`SnapshotAuditInterceptor` 字段反射缓存**（每个 Entity 类的 `setAccessible()` 仅执行一次，缓存到
  `ConcurrentHashMap`）
- [x] **`MicrometerJpaPlusMetrics` Timer 实例缓存**（按 tag 组合键缓存 `Timer`，消除每次埋点的 Registry 查找）
- [x] **`SqlCompilerRegistry` 未知方言 `WARN` 日志**（回退到通用编译器时自动打印可诊断日志）
- [x] **`SnowflakeIdGenerator` 时钟回退异常消息一致性**（快照 `state.get()` 一次，消除 TOCTOU 竞态下的消息不一致）
- [x] **`AbstractSqlCompiler` unknown `QueryType` 防御**（`switch` 增加 `default throw`，编译器强制穷举新操作类型）

### 近期（已全部完成）

- [x] **`@ShardingQuery` 支持返回 `Optional` 和 `Page<T>` 类型自动适配**（null 安全，反射软依赖 spring-data）
- [x] **`SnapshotAuditInterceptor` 支持自定义快照序列化策略**（`SnapshotSerializer` SPI + `JacksonSnapshotSerializer`）
- [x] **`FieldEngine` 提供热重载钩子**（`registerHandler` / `unregisterHandler` / `clearHandlerCache`，volatile +
  synchronized 线程安全）

### 中期（持续优化）

- [x] **`CrossShardQueryExecutor` 支持流式归并**（`executeAsStream()` 惰性 `Stream<T>`，分片按需加载，峰值内存大幅降低）
- [x] **`CachedDictProvider` 支持 MQ 广播失效**（`KafkaDictCacheEvictAdapter` + `RocketMQDictCacheEvictAdapter`
  适配器模板，无需引入额外依赖）
- [x] **分片模块 Spring Boot Test 全链路集成测试**（`ShardingQueryAspectIntegrationTest`：AOP 拦截、SpEL 路由、上下文生命周期、流式
  API 惰性验证）

### 规划中

---

## 📋 版本信息

| 属性       | 值              |
|----------|----------------|
| Group    | `io.github.guanxiangkai` |
| Version  | 各模块版本以 `gradle/module-versions.properties` 为准 |
| JDK      | `25`           |
| Encoding | `UTF-8`        |

---

## 📄 开源协议

本项目基于 [Apache License 2.0](LICENSE) 开源协议发布。

```text
Copyright 2026 actomize

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/guanxiangkai">guanxiangkai</a>
</p>
